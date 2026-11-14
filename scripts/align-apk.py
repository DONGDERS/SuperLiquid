#!/usr/bin/env python3
"""Rebuild an APK so PackageInstaller accepts it.

Android enforces these invariants on an APK whose manifest declares
extractNativeLibs="false" (AGP's default for release builds):

  * every lib/**/*.so must be STORED (uncompressed);
  * every lib/**/*.so payload must start on a page boundary. 16 KiB satisfies
    both 4 KiB and 16 KiB page devices (Android 15+), so that is the default;
  * resources.arsc must be STORED and 4-byte aligned.

zipalign leaves resources.arsc misaligned on the release APKs AGP produces
here, and `zipalign -p` does not reliably fix the .so offsets either, so this
rewrites the archive itself.

The archive is written by hand: every local header offset comes from the byte
counter this script actually writes with. An earlier version predicted offsets
with ZipFile.fp.tell() and writestr(), which drifted 20 bytes on the real AGP
output and silently broke the .so page alignment -- the install then failed
with INSTALL_FAILED_INVALID_APK "Failed to extract native libraries, res=-2".

usage: align-apk.py <in.apk> <out.apk> [page-bytes]
"""
import struct
import sys
import zipfile

ALIGN = 4
DEFAULT_PAGE = 16384
EXTRA_ID = 0xA11E  # Opaque padding; 0xD935 is reserved for Android alignment metadata.

LOCAL_SIG = 0x04034B50
CENTRAL_SIG = 0x02014B50
EOCD_SIG = 0x06054B50


def read_entries(path):
    """Return raw entry records, payloads copied verbatim from the source."""
    with open(path, 'rb') as fh:
        blob = fh.read()
    entries = []
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            off = info.header_offset
            if blob[off:off + 4] != b'PK\x03\x04':
                raise SystemExit(f'bad local header for {info.filename}')
            name_len, extra_len = struct.unpack('<HH', blob[off + 26:off + 30])
            start = off + 30 + name_len + extra_len
            payload = blob[start:start + info.compress_size]
            if len(payload) != info.compress_size:
                raise SystemExit(f'truncated payload for {info.filename}')
            if info.compress_size >= 0xFFFFFFFF or info.file_size >= 0xFFFFFFFF:
                raise SystemExit(f'zip64 entry unsupported: {info.filename}')
            entries.append({
                'name': info.filename,
                'method': info.compress_type,
                'crc': info.CRC,
                'csize': info.compress_size,
                'usize': info.file_size,
                'date_time': info.date_time,
                'external_attr': info.external_attr,
                'create_system': info.create_system,
                'utf8': bool(info.flag_bits & 0x800) or not info.filename.isascii(),
                'payload': payload,
            })
    return entries


def alignment_for(name):
    return PAGE if (name.startswith('lib/') and name.endswith('.so')) else ALIGN


def dos_stamp(date_time):
    year, month, day, hour, minute, second = date_time
    if year < 1980:
        year, month, day, hour, minute, second = 1980, 1, 1, 0, 0, 0
    dos_date = ((year - 1980) << 9) | (month << 5) | day
    dos_time = (hour << 11) | (minute << 5) | (second // 2)
    return dos_time, dos_date


def padding(pos, name_len, align):
    """Extra field that lands this entry's payload on an ``align`` boundary."""
    need = (-(pos + 30 + name_len)) % align
    if need == 0:
        return b''
    if need < 4:  # an extra field needs a 4-byte header; widen by one block
        need += align
    return struct.pack('<HH', EXTRA_ID, need - 4) + b'\x00' * (need - 4)


def write_apk(entries, dst):
    central = []
    with open(dst, 'wb') as out:
        for entry in entries:
            name = entry['name'].encode('utf-8')
            align = alignment_for(entry['name'])
            pos = out.tell()
            extra = padding(pos, len(name), align)
            dos_time, dos_date = dos_stamp(entry['date_time'])
            flags = 0x800 if entry['utf8'] else 0
            header = struct.pack(
                '<IHHHHHIIIHH',
                LOCAL_SIG, 20, flags, entry['method'], dos_time, dos_date,
                entry['crc'], entry['csize'], entry['usize'],
                len(name), len(extra),
            )
            out.write(header)
            out.write(name)
            out.write(extra)
            data_at = out.tell()
            if data_at != pos + 30 + len(name) + len(extra):
                raise SystemExit(f'local header drift on {entry["name"]}')
            if data_at % align:
                raise SystemExit(f'padding failed on {entry["name"]}: {data_at} % {align}')
            out.write(entry['payload'])
            central.append((entry, name, extra, pos, flags, dos_time, dos_date))
            print(f'{entry["name"]:55s} align={align:5d} pad={len(extra):5d} at={data_at}')
        cd_start = out.tell()
        for entry, name, extra, pos, flags, dos_time, dos_date in central:
            version_made_by = (entry['create_system'] << 8) | 20
            out.write(struct.pack(
                '<IHHHHHHIIIHHHHHII',
                CENTRAL_SIG, version_made_by, 20, flags, entry['method'],
                dos_time, dos_date, entry['crc'], entry['csize'], entry['usize'],
                len(name), len(extra), 0, 0, 0, entry['external_attr'], pos,
            ))
            out.write(name)
            out.write(extra)
        cd_size = out.tell() - cd_start
        out.write(struct.pack(
            '<IHHHHIIH', EOCD_SIG, 0, 0, len(central), len(central),
            cd_size, cd_start, 0,
        ))


def main():
    global PAGE
    argv = sys.argv[1:]
    if len(argv) not in (2, 3):
        print('usage: align-apk.py <in.apk> <out.apk> [page-bytes]', file=sys.stderr)
        return 2
    PAGE = int(argv[2]) if len(argv) == 3 else DEFAULT_PAGE
    if PAGE < ALIGN or PAGE & (PAGE - 1):
        print(f'page size must be a power of two >= {ALIGN}', file=sys.stderr)
        return 2
    write_apk(read_entries(argv[0]), argv[1])
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
