#!/bin/sh
# App Store screenshots (iPhone 6.9", 1320x2868, dark) from the demo node, saved to appstore/screenshots/.
#   ./screenshots.sh [simulator name, default meshplus-iphone: an iPhone 18 Pro Max]
# Any simulator works, e.g. an iPhone Duo for its cover (1398x2034) and inner (2007x2853) sets. Set SHOTS_DIR to save
# them somewhere else (relative paths are from ios/).
set -e
cd "$(dirname "$0")"
SIM="${1:-meshplus-iphone}"
OUT="${SHOTS_DIR:-appstore/screenshots}"
xcrun simctl boot "$SIM" 2>/dev/null || true
xcrun simctl ui "$SIM" appearance dark
xcrun simctl status_bar "$SIM" override --time 9:41 --dataNetwork wifi --wifiMode active --wifiBars 3 \
  --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100
out=$(mktemp -d)
TEST_RUNNER_SHOTS="$out" xcodebuild -project MeshPlus.xcodeproj -scheme MeshPlus -destination "platform=iOS Simulator,name=$SIM" \
  -derivedDataPath build test -only-testing:UITests/Screenshots 2>&1 | grep -E 'error:|TEST SUCC|TEST FAIL' || true
mkdir -p "$OUT"
cp "$out"/*.png "$OUT"/
ls "$OUT"
