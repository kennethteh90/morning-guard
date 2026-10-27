#!/usr/bin/env bash
# Capture iPhone guide screenshots into docs/ios/guide-assets/ (simulator, Debug + -MGScreenshotMode).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS="$ROOT/ios"
OUT="$ROOT/docs/ios/guide-assets"
BUNDLE_ID="com.morningguard.app"
SIM_ID="${SIM_ID:-748287C2-3ABE-418C-B2BB-5999BB8D9208}"
DERIVED="$IOS/build/GuideScreenshots"
APP="$DERIVED/Build/Products/Debug-iphonesimulator/MorningGuard.app"

mkdir -p "$OUT"

echo "Building MorningGuard (Debug, simulator)…"
xcodebuild \
  -project "$IOS/MorningGuard.xcodeproj" \
  -scheme MorningGuard \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=$SIM_ID" \
  -derivedDataPath "$DERIVED" \
  build \
  CODE_SIGNING_ALLOWED=NO \
  | tail -3

xcrun simctl boot "$SIM_ID" 2>/dev/null || true
xcrun simctl bootstatus "$SIM_ID" -b

xcrun simctl uninstall "$SIM_ID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install "$SIM_ID" "$APP"

capture() {
  local scene="$1"
  local file="$2"
  echo "Capturing $file (scene=$scene)…"
  xcrun simctl terminate "$SIM_ID" "$BUNDLE_ID" 2>/dev/null || true
  xcrun simctl launch "$SIM_ID" "$BUNDLE_ID" -MGScreenshotMode "-MGGuideScene" "$scene" >/dev/null
  sleep 5
  xcrun simctl io "$SIM_ID" screenshot "$OUT/$file"
}

capture home mg-home.png
capture goal mg-goal.png
capture settings mg-settings-shields.png
capture focus mg-onboarding-focus.png
capture apps mg-onboarding-apps.png

# Remove stray files from manual runs
rm -f "$OUT/mg-focus.png" "$OUT/mg-apps.png" "$OUT/mg-settings.png"

echo "Wrote PNGs to $OUT"
