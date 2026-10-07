#!/usr/bin/env bash
# Post-build: inject META-INF/xposed into the unsigned release APK, zipalign,
# sign with apksigner. Same pipeline the pre-0.4.0 build.sh used — AGP signs
# nothing here, so the injection does not invalidate any signature.
set -euo pipefail

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BT="/home/z/work/WeChat-LiquidGlass/tools/bt/android-14"
OUT="$PROJ/app/build/outputs/apk/release"
UNSIGNED="$OUT/app-release-unsigned.apk"

[ -f "$UNSIGNED" ] || { echo "missing $UNSIGNED — run gradle first" >&2; exit 1; }

STAGED="$PROJ/build/staged.apk"
cp "$UNSIGNED" "$STAGED"

# 1. inject META-INF/xposed/* at the APK root
python3 - "$STAGED" "$PROJ" <<'EOF'
import sys, zipfile, shutil, os
apk, proj = sys.argv[1], sys.argv[2]
tmp = apk + ".tmp"
with zipfile.ZipFile(apk) as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    for root, _, files in os.walk(os.path.join(proj, "META-INF")):
        for f in files:
            full = os.path.join(root, f)
            arc = os.path.relpath(full, proj)
            zout.write(full, arc)
shutil.move(tmp, apk)
print("META-INF injected")
EOF

# 2. zipalign (uncompressed .so / resources need 4-byte alignment pre-sign)
"$BT/zipalign" -f 4 "$STAGED" "$OUT/app-release-aligned.apk"

# 3. sign with the project keystore (same key as 0.3.9 → in-place update ok)
java -cp "$BT/lib/apksigner.jar" com.android.apksigner.ApkSignerTool sign \
    --ks "$PROJ/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$PROJ/SuperLiquid-v$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$PROJ/app/build.gradle.kts").apk" \
    "$OUT/app-release-aligned.apk"

FINAL=$(ls "$PROJ"/SuperLiquid-v*.apk | tail -1)
java -cp "$BT/lib/apksigner.jar" com.android.apksigner.ApkSignerTool verify --print-certs "$FINAL" | head -3
ls -la "$FINAL"
