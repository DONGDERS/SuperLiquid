#!/usr/bin/env python3
"""Fail the build if PackageInstaller would reject the APK.

Invariants checked (AGP ships extractNativeLibs="false" for release builds):

  * resources.arsc must be STORED and 4-byte aligned (Android 11+);
  * every lib/**/*.so must be STORED and its payload must start on a page
    boundary -- otherwise the install fails with INSTALL_FAILED_INVALID_APK
    "Failed to extract native libraries, res=-2";
  * every STORED entry must be at least 4-byte aligned.

An earlier version only looked at resources.arsc and the .so compression
method, so a misaligned .so slipped through CI and broke the install.

usage: check-apk-align.py <apk> [page-bytes]
"""
import struct
import sys
import zipfile

ALIGN = 4
DEFAULT_PAGE = 16384


def payload_offset(blob, info):
    off = info.header_offset
    if blob[off:off + 4] != b'PK\x03\x04':
        raise ValueError(f'bad local header for {info.filename}')
    name_len, extra_len = struct.unpack('<HH', blob[off + 26:off + 30])
    return off + 30 + name_len + extra_len


def main():
    argv = sys.argv[1:]
    if not argv:
        print('usage: check-apk-align.py <apk> [page-bytes]', file=sys.stderr)
        return 2
    page = int(argv[1]) if len(argv) > 1 else DEFAULT_PAGE
    failures = []
    try:
        with open(argv[0], 'rb') as fh:
            blob = fh.read()
        z = zipfile.ZipFile(argv[0])
    except Exception as exc:  # noqa: BLE001 - report and fail the build
        print(f'cannot open APK: {exc}', file=sys.stderr)
        return 1
    with z:
        if z.testzip() is not None:
            failures.append('zip integrity check failed')
        for info in z.infolist():
            try:
                at = payload_offset(blob, info)
            except ValueError as exc:
                failures.append(str(exc))
                continue
            stored = info.compress_type == zipfile.ZIP_STORED
            if info.filename == 'resources.arsc':
                if not stored:
                    failures.append('resources.arsc is deflated (must be STORED)')
                elif at % ALIGN:
                    failures.append(
                        f'resources.arsc misaligned: offset {at} (mod {ALIGN} = {at % ALIGN})')
            if info.filename.startswith('lib/') and info.filename.endswith('.so'):
                if not stored:
                    failures.append(f'{info.filename} is deflated (must be STORED)')
                elif at % page:
                    failures.append(
                        f'{info.filename} not page aligned: offset {at} '
                        f'(mod {page} = {at % page})')
                else:
                    print(f'ok  {info.filename} page-aligned at {at}')
            if stored and at % ALIGN:
                failures.append(
                    f'{info.filename} not {ALIGN}-byte aligned: offset {at} '
                    f'(mod {ALIGN} = {at % ALIGN})')
    if failures:
        for failure in failures:
            print(f'FATAL: {failure}', file=sys.stderr)
        return 1
    print(f'APK alignment/compression invariants OK (page={page})')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
