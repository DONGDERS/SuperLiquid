#!/usr/bin/env bash
# Post-build: inject META-INF/xposed into the unsigned release APK, zipalign,
# sign with apksigner.
#
# The injection must APPEND entries without rewriting the archive: AGP emits
# native libs uncompressed (extractNativeLibs=false) and re-deflating them
# makes PackageInstaller fail with INSTALL_FAILED_INVALID_APK
# ("Failed to extract native libraries, res=-2"). `zip` only appends.
set -euo pipefail

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BT="/home/z/work/WeChat-LiquidGlass/tools/bt/android-14"
OUT="$PROJ/app/build/outputs/apk/release"
UNSIGNED="$OUT/app-release-unsigned.apk"

[ -f "$UNSIGNED" ] || { echo "missing $UNSIGNED — run gradle first" >&2; exit 1; }

STAGED="$PROJ/build/staged.apk"
mkdir -p "$PROJ/build"
cp "$UNSIGNED" "$STAGED"

# 1. append META-INF/xposed/* at the APK root (existing entries untouched)
( cd "$PROJ" && zip -q -X "$STAGED" \
    META-INF/xposed/module.prop \
    META-INF/xposed/java_init.list \
    META-INF/xposed/module.scope \
    META-INF/xposed/scope.list )
echo "META-INF injected (append-only)"

# 2. zipalign (stored .so entries get page alignment pre-sign)
"$BT/zipalign" -f 4 "$STAGED" "$OUT/app-release-aligned.apk"

# sanity: every lib/*.so must be 'Stored'
BAD=$(unzip -v "$OUT/app-release-aligned.apk" 'lib/*' | awk '$2 ~ /Defl/ {print $NF}')
if [ -n "$BAD" ]; then
    echo "FATAL: deflated native libs: $BAD" >&2
    exit 1
fi

# 3. sign with the project keystore (same key as 0.3.9 → in-place update ok)
VER="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$PROJ/app/build.gradle.kts")"
java -cp "$BT/lib/apksigner.jar" com.android.apksigner.ApkSignerTool sign \
    --ks "$PROJ/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$PROJ/SuperLiquid-v$VER.apk" \
    "$OUT/app-release-aligned.apk"

FINAL="$PROJ/SuperLiquid-v$VER.apk"
java -cp "$BT/lib/apksigner.jar" com.android.apksigner.ApkSignerTool verify "$FINAL" && echo "signed ok"
ls -la "$FINAL"
