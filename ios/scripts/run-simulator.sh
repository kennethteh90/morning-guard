#!/usr/bin/env bash
# Build and run Morning Guard on the iOS Simulator (no paid Apple Developer account required).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

if ! xcode-select -p 2>/dev/null | grep -q "Xcode.app"; then
  echo "Install Xcode from the App Store, then:" >&2
  echo "  sudo xcode-select -s /Applications/Xcode.app/Contents/Developer" >&2
  exit 1
fi

SIM_NAME="${SIMULATOR_NAME:-iPhone 17}"

"$SCRIPT_DIR/generate-xcodeproj.sh"

xcodebuild \
  -project MorningGuard.xcodeproj \
  -scheme MorningGuard \
  -destination "platform=iOS Simulator,name=${SIM_NAME}" \
  -sdk iphonesimulator \
  CODE_SIGNING_ALLOWED=NO \
  build

DERIVED="$(xcodebuild -project MorningGuard.xcodeproj -scheme MorningGuard -sdk iphonesimulator -showBuildSettings 2>/dev/null | awk -F' = ' '/TARGET_BUILD_DIR/ {print $2; exit}')"
APP="${DERIVED}/MorningGuard.app"

if [[ ! -d "$APP" ]]; then
  echo "Could not find MorningGuard.app under DerivedData." >&2
  exit 1
fi

UDID="$(xcrun simctl list devices available -j | python3 -c "
import json,sys
name=sys.argv[1]
for d in json.load(sys.stdin)['devices'].values():
  for dev in d:
    if dev.get('name')==name and dev.get('isAvailable'):
      print(dev['udid']); raise SystemExit
raise SystemExit('Simulator not found: '+name)
" "$SIM_NAME")"

xcrun simctl boot "$UDID" 2>/dev/null || true
# Xcode 27+ uses Device Hub instead of Simulator.app
if [[ -d /Applications/Xcode.app/Contents/Applications/DeviceHub.app ]]; then
  open -a DeviceHub
else
  open -a Simulator
fi
xcrun simctl install "$UDID" "$APP"
xcrun simctl launch "$UDID" com.morningguard.app
echo "Launched Morning Guard on ${SIM_NAME}."
