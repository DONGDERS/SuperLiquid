#!/usr/bin/env python3
"""Rewrite an APK with every entry 4-byte aligned (and .so page-aligned).

zipalign silently leaves resources.arsc misaligned on the release APKs AGP
produces here, which Android 11+ rejects. This rebuilds the archive from
scratch, preserving each entry's compression and metadata, and pads via a
local extra field so every entry's data starts on the required boundary.

usage: align-apk.py <in.apk> <out.apk>
"""
import struct
import sys
import zipfile

ALIGN = 4
PAGE = 4096
EXTRA_ID = 0xd935


def pad_for(offset: int, name_len: int, align: int) -> bytes:
    """Extra field placing this entry's data on an ``align`` boundary."""
    rem = (offset + 30 + name_len) % align
    if rem == 0:
        return b''
    need = align - rem
    if need < 4:
        need += align
    return struct.pack('<HH', EXTRA_ID, need - 4) + b'\x00' * (need - 4)


def main() -> int:
    if len(sys.argv) != 3:
        print('usage: align-apk.py <in.apk> <out.apk>', file=sys.stderr)
        return 2
    src, dst = sys.argv[1], sys.argv[2]

    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w') as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            align = PAGE if info.filename.startswith('lib/') else ALIGN
            zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            zi.compress_type = info.compress_type
            zi.external_attr = info.external_attr
            zi.internal_attr = info.internal_attr
            zi.create_system = info.create_system
            offset = zout.fp.tell()
            name_len = len(info.filename.encode('utf-8'))
            extra = pad_for(offset, name_len, align)
            # Our own pad must itself not break the boundary; recompute once.
            if (offset + 30 + name_len + len(extra)) % align:
                extra = pad_for(offset, name_len + len(extra), align)
            zi.extra = extra
            zout.writestr(zi, data)
            print(f'{info.filename:55s} align={align:5d} pad={len(extra)}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
