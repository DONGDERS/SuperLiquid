#!/usr/bin/env python3
"""Fail the build if an APK would be rejected by PackageInstaller.

Two invariants:
  * resources.arsc must be STORED and 4-byte aligned (Android 11+);
  * every lib/*.so must be STORED (AGP ships extractNativeLibs=false).

Alignment of ordinary resources is a performance detail and is only reported.
"""
import sys
import zipfile

ALIGN = 4


def data_offset(info: zipfile.ZipInfo) -> int:
    return info.header_offset + 30 + len(info.filename.encode('utf-8')) + len(info.extra)


def main() -> int:
    if len(sys.argv) != 2:
        print('usage: check-apk-align.py <apk>', file=sys.stderr)
        return 2
    failures = []
    try:
        z = zipfile.ZipFile(sys.argv[1])
    except Exception as exc:            # noqa: BLE001 - report and fail
        print(f'cannot open APK: {exc}', file=sys.stderr)
        return 1

    with z:
        if z.testzip() is not None:
            failures.append('zip integrity check failed')
        stored, deflated = [], []
        for info in z.infolist():
            if info.compress_type == zipfile.ZIP_STORED:
                stored.append(info)
            else:
                deflated.append(info)

        for info in z.infolist():
            if info.filename == 'resources.arsc':
                if info.compress_type != zipfile.ZIP_STORED:
                    failures.append('resources.arsc is deflated (must be STORED)')
                off = data_offset(info)
                if off % ALIGN:
                    failures.append(f'resources.arsc misaligned: offset {off} (mod {ALIGN} = {off % ALIGN})')
            if info.filename.startswith('lib/') and info.filename.endswith('.so'):
                if info.compress_type != zipfile.ZIP_STORED:
                    failures.append(f'{info.filename} is deflated (must be STORED)')

        misaligned = [i for i in stored if data_offset(i) % ALIGN]
        if misaligned:
            print('note: %d stored entries are not %d-byte aligned ' '(harmless for install):' % (len(misaligned), ALIGN))
            for i in misaligned[:10]:
                print('   ', i.filename)

    if failures:
        for f in failures:
            print(f'FATAL: {f}', file=sys.stderr)
        return 1
    print('APK alignment/compression invariants OK')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
