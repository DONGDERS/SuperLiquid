#!/usr/bin/env bash
# Downloads the toolchain build.sh expects into $TOOL_ROOT (default ./tools).
set -euo pipefail

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOL_ROOT="${TOOL_ROOT:-$PROJ/tools}"
mkdir -p "$TOOL_ROOT"
cd "$TOOL_ROOT"

fetch() {
    local url="$1" out="$2"
    [ -s "$out" ] && { echo "  have $out"; return; }
    echo "  fetching $out"
    curl -sSLf -o "$out" "$url"
}

echo "[1/3] libraries"
fetch https://repo1.maven.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar api-102.aar

echo "[2/3] android sdk pieces"
fetch https://dl.google.com/android/repository/build-tools_r34-linux.zip bt34.zip
fetch https://dl.google.com/android/repository/platform-34-ext7_r03.zip plat34.zip

echo "[3/3] unpack"
# Unpacked via python, for the same reason build.sh zips that way: NixOS ships
# no unzip(1). The exec bit has to be carried over by hand — aapt2, d8 and
# zipalign come out of the archive as plain files otherwise.
python3 - <<'PYUNZIP'
import os, zipfile
for src, dst in (("api-102.aar", "xapi"), ("bt34.zip", "bt"), ("plat34.zip", "plat")):
    os.makedirs(dst, exist_ok=True)
    with zipfile.ZipFile(src) as z:
        for info in z.infolist():
            out = z.extract(info, dst)
            mode = info.external_attr >> 16
            if mode:
                os.chmod(out, mode)
    print(f"  unpacked {src} -> {dst}/")
PYUNZIP
chmod +x bt/*/aapt2 bt/*/d8 bt/*/zipalign 2>/dev/null || true

echo "done. tools in $TOOL_ROOT"
