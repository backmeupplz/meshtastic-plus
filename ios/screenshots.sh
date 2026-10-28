#!/bin/sh
# Takes the App Store screenshots (UITests/Screenshots) from a release build in every app language on one simulator,
# into $OUT/<language>/<n>-<name>.png. scripts/shots/compose.py then puts them under each locale's captions.
#   DEVICE  simulator udid (default: the booted iPhone 18 Pro Max, for the 6.9" set); an iPhone Duo gives its cover display
#   LANGS   languages to take, e.g. "de ar" (default: every language in Localizable.xcstrings)
#   OUT     output folder (default ./store-shots)
set -e
cd "$(dirname "$0")"
DEVICE="${DEVICE:-$(xcrun simctl list devices booted | sed -n 's/.*iPhone 18 Pro Max (\([0-9A-F-]*\)) (Booted).*/\1/p' | head -1)}"
[ -n "$DEVICE" ] || { echo "Boot an iPhone 18 Pro Max simulator or set DEVICE" >&2; exit 1; }
LANGS="${LANGS:-$(python3 -c 'import json; s = json.load(open("MeshPlus/Localizable.xcstrings"))["strings"]; print(" ".join(sorted({l for v in s.values() for l in v.get("localizations", {})} | {"en"})))')}"
OUT="$(mkdir -p "${OUT:-store-shots}" && cd "${OUT:-store-shots}" && pwd)"
DEST="platform=iOS Simulator,id=$DEVICE"
DATA="${DERIVED_DATA:-build-shots}"
xcrun simctl boot "$DEVICE" 2>/dev/null || true
xcrun simctl ui "$DEVICE" appearance dark
xcrun simctl status_bar "$DEVICE" override --time 9:41 --dataNetwork wifi --wifiMode active --wifiBars 3 --cellularMode active \
  --cellularBars 4 --batteryState charged --batteryLevel 100
xcodebuild -project MeshPlus.xcodeproj -scheme MeshPlus -configuration Release -destination "$DEST" -derivedDataPath "$DATA" \
  ENABLE_TESTABILITY=YES build-for-testing > "$OUT/build.log" 2>&1 || { tail -30 "$OUT/build.log"; exit 1; }
failed=""
for lang in $LANGS; do
  rm -rf "${OUT:?}/$lang"
  if TEST_RUNNER_SHOTS="$OUT/$lang" TEST_RUNNER_STORE_LANG="$lang" \
    xcodebuild -project MeshPlus.xcodeproj -scheme MeshPlus -configuration Release -destination "$DEST" -derivedDataPath "$DATA" \
    -only-testing:UITests/Screenshots/testScreenshots -collect-test-diagnostics never test-without-building > "$OUT/$lang.log" 2>&1; then
    echo "$lang: $(ls "$OUT/$lang" | tr '\n' ' ')"
  else
    grep -E 'error|failed|XCTAssert' "$OUT/$lang.log" | head -5
    echo "$lang: failed, see $OUT/$lang.log"
    failed="$failed $lang"
  fi
done
[ -z "$failed" ] || { echo "Failed:$failed" >&2; exit 1; }
