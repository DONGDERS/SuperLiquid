#!/usr/bin/env python3
"""Append META-INF/xposed to a release APK, keeping the archive installable.

AGP strips META-INF during the resource merge, and LSPosed only discovers a
module from those entries at the APK root, so they have to be added after the
build. Two rules matter:

* nothing already in the archive may be rewritten — AGP ships native libs
  stored and page-aligned, and re-deflating them fails the install with
  INSTALL_FAILED_INVALID_APK ("Failed to extract native libraries");
* every STORED entry's data must stay 4-byte aligned, resources.arsc included,
  or Android 11+ refuses the APK.

Appending through zipfile keeps existing entries byte-identical, but its new
local headers are not guaranteed aligned, so each appended entry gets an
alignment extra field of its own.
"""
import os
import struct
import sys
import zipfile

ALIGN = 4
# Private extra-field id (the 0x0000..0x7fff range is free for local use).
ALIGN_EXTRA_ID = 0xd935


def _padding(header_offset: int, name_len: int, extra_len: int) -> bytes:
    """Extra field that lands this entry's data on an ALIGN boundary."""
    data_off = header_offset + 30 + name_len + extra_len
    rem = data_off % ALIGN
    if rem == 0:
        return b''
    need = ALIGN - rem
    if need < 4:
        # An extra field needs a 4-byte header; widen by one full block.
        need += ALIGN
    return struct.pack('<HH', ALIGN_EXTRA_ID, need - 4) + b'\x00' * (need - 4)


def main() -> int:
    if len(sys.argv) != 3:
        print('usage: inject-xposed.py <apk> <xposed-dir>', file=sys.stderr)
        return 2
    apk, src = sys.argv[1], sys.argv[2]
    if not os.path.isdir(src):
        print(f'missing {src}', file=sys.stderr)
        return 1

    entries = []
    for root, _, files in os.walk(src):
        for name in sorted(files):
            full = os.path.join(root, name)
            entries.append((full, os.path.relpath(full, '.')))
    if not entries:
        print('nothing to inject', file=sys.stderr)
        return 1

    # 'a' mode appends without touching existing local headers.
    with zipfile.ZipFile(apk, 'a') as z:
        for full, arc in entries:
            info = zipfile.ZipInfo(arc, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            # Predict where this entry's local header will start, then pad.
            offset = z.fp.tell() if z.fp else 0
            name_len = len(arc.encode('utf-8'))
            info.extra = _padding(offset, name_len, 0)
            with open(full, 'rb') as fh:
                z.writestr(info, fh.read())
            print('added', arc, 'pad', len(info.extra))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
