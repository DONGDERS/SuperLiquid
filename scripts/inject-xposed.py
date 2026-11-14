#!/usr/bin/env python3
"""Append META-INF/xposed to a release APK.

AGP strips META-INF during the resource merge, and LSPosed only discovers a
module from those entries at the APK root. Appending (never rewriting) keeps
AGP's stored, uncompressed native libraries byte-identical and aligned.
"""
import os
import sys
import zipfile


def main() -> int:
    if len(sys.argv) != 3:
        print('usage: inject-xposed.py <apk> <xposed-dir>', file=sys.stderr)
        return 2
    apk, src = sys.argv[1], sys.argv[2]
    if not os.path.isdir(src):
        print(f'missing {src}', file=sys.stderr)
        return 1
    added = 0
    with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(src):
            for name in sorted(files):
                full = os.path.join(root, name)
                arc = os.path.relpath(full, '.')
                z.write(full, arc)
                print('added', arc)
                added += 1
    if added == 0:
        print('nothing to inject', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
