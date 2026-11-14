#!/usr/bin/env python3
"""Append META-INF/xposed to a release APK, keeping the archive installable.

AGP strips META-INF during the resource merge, and LSPosed only discovers a
module from entries at the APK root, so they have to be added after the build.

Rules that matter, all of them enforced by PackageInstaller:
  * nothing already in the archive may be rewritten -- AGP ships native libs
    stored and page-aligned, and re-deflating or shifting them fails the
    install with INSTALL_FAILED_INVALID_APK ("Failed to extract native
    libraries", res=-2);
  * appended entries are padded so their payload starts on a 4-byte boundary.

The original bytes are copied through untouched up to the central directory,
which is rebuilt with the appended entries. An earlier version predicted local
header offsets with ZipFile.fp.tell() and writestr(); that drift is what broke
the .so page alignment, so offsets here come from the real write position.

usage: inject-xposed.py <apk> <xposed-dir> [page-bytes]
"""
import os
import struct
import sys
import zlib

ALIGN = 4
DEFAULT_PAGE = 16384
EXTRA_ID = 0xD935

LOCAL_SIG = 0x04034B50
CENTRAL_SIG = 0x02014B50
EOCD_SIG = 0x06054B50


def dos_stamp(date_time):
    year, month, day, hour, minute, second = date_time
    if year < 1980:
        year, month, day, hour, minute, second = 1980, 1, 1, 0, 0, 0
    return (hour << 11) | (minute << 5) | (second // 2), ((year - 1980) << 9) | (month << 5) | day


def padding(pos, name_len, align):
    need = (-(pos + 30 + name_len)) % align
    if need == 0:
        return b''
    if need < 4:
        need += align
    return struct.pack('<HH', EXTRA_ID, need - 4) + b'\x00' * (need - 4)


def find_eocd(blob):
    start = max(0, len(blob) - 0xFFFF - 22)
    for pos in range(len(blob) - 22, start - 1, -1):
        if blob[pos:pos + 4] == b'PK\x05\x06':
            return pos
    raise SystemExit('no end-of-central-directory record found')


def read_central(blob):
    eocd = find_eocd(blob)
    count, cd_size, cd_off = struct.unpack('<HII', blob[eocd + 10:eocd + 20])
    if cd_off == 0xFFFFFFFF or count == 0xFFFF:
        raise SystemExit('zip64 archives are not supported')
    entries = []
    pos = cd_off
    for _ in range(count):
        if blob[pos:pos + 4] != b'PK\x01\x02':
            raise SystemExit('corrupt central directory')
        fields = struct.unpack('<IHHHHHHIIIHHHHHII', blob[pos:pos + 46])
        name_len, extra_len, comment_len = fields[10], fields[11], fields[12]
        name = blob[pos + 46:pos + 46 + name_len]
        extra = blob[pos + 46 + name_len:pos + 46 + name_len + extra_len]
        entries.append({
            'name': name,
            'method': fields[4],
            'crc': fields[7],
            'csize': fields[8],
            'usize': fields[9],
            'flags': fields[3],
            'dos_time': fields[5],
            'dos_date': fields[6],
            'external_attr': fields[15],
            'version_made_by': fields[1],
            'local_off': fields[16],
            'extra': extra,
        })
        pos += 46 + name_len + extra_len + comment_len
    return entries, cd_off


def main():
    argv = sys.argv[1:]
    if len(argv) not in (2, 3):
        print('usage: inject-xposed.py <apk> <xposed-dir> [page-bytes]', file=sys.stderr)
        return 2
    apk, src = argv[0], argv[1]
    page = int(argv[2]) if len(argv) == 3 else DEFAULT_PAGE
    if not os.path.isdir(src):
        print(f'missing {src}', file=sys.stderr)
        return 1

    with open(apk, 'rb') as fh:
        blob = fh.read()
    entries, cd_off = read_central(blob)
    existing = {e['name'].decode('utf-8') for e in entries}

    # Entries must land at META-INF/xposed/... regardless of how <xposed-dir>
    # was spelled on the command line (absolute path, trailing slash, ..).
    root_arc = 'META-INF/xposed'
    additions = []
    for root, _, files in os.walk(src):
        for name in sorted(files):
            full = os.path.join(root, name)
            inner = os.path.relpath(full, src).replace(os.sep, '/')
            arc = f'{root_arc}/{inner}'
            if arc in existing:
                raise SystemExit(f'{arc} already present; refusing to duplicate')
            with open(full, 'rb') as fh:
                additions.append((arc, fh.read()))
    if not additions:
        print('nothing to inject', file=sys.stderr)
        return 1

    # Everything before the central directory stays byte-identical.
    with open(apk, 'wb') as out:
        out.write(blob[:cd_off])
        for arc, data in additions:
            name = arc.encode('utf-8')
            pos = out.tell()
            extra = padding(pos, len(name), ALIGN)
            dos_time, dos_date = dos_stamp((2026, 1, 1, 0, 0, 0))
            out.write(struct.pack(
                '<IHHHHHIIIHH',
                LOCAL_SIG, 20, 0x800, 0, dos_time, dos_date,
                zlib.crc32(data), len(data), len(data), len(name), len(extra),
            ))
            out.write(name)
            out.write(extra)
            data_at = out.tell()
            if data_at % ALIGN:
                raise SystemExit(f'padding failed on {arc}')
            out.write(data)
            entries.append({
                'name': name, 'method': 0, 'crc': zlib.crc32(data),
                'csize': len(data), 'usize': len(data), 'flags': 0x800,
                'dos_time': dos_time, 'dos_date': dos_date,
                'external_attr': 0o644 << 16, 'version_made_by': 20,
                'local_off': pos, 'extra': extra,
            })
            print(f'added {arc} pad={len(extra)} at={data_at}')

        cd_start = out.tell()
        for entry in entries:
            out.write(struct.pack(
                '<IHHHHHHIIIHHHHHII',
                CENTRAL_SIG, entry['version_made_by'], 20, entry['flags'],
                entry['method'], entry['dos_time'], entry['dos_date'],
                entry['crc'], entry['csize'], entry['usize'],
                len(entry['name']), len(entry['extra']), 0, 0, 0,
                entry['external_attr'], entry['local_off'],
            ))
            out.write(entry['name'])
            out.write(entry['extra'])
        cd_size = out.tell() - cd_start
        out.write(struct.pack(
            '<IHHHHIIH', EOCD_SIG, 0, 0, len(entries), len(entries),
            cd_size, cd_start, 0,
        ))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())