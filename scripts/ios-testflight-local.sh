#!/usr/bin/env bash
# Build and upload to TestFlight from your Mac (uses Local.xcconfig + App Store Connect API key).
#
# Usage:
#   ./scripts/ios-testflight-local.sh
#   (reads APP_STORE_CONNECT_API_ISSUER_ID from ios/Config/Local.xcconfig if set)
#
# Optional:
#   APP_STORE_CONNECT_API_KEY_PATH  (default: ~/Keys/morning-guard/AuthKey_*.p8 if exactly one)
#   APP_STORE_CONNECT_API_KEY_ID    (default: parsed from AuthKey_<ID>.p8 filename)
#   IOS_DEVELOPMENT_TEAM            (default: DEVELOPMENT_TEAM in ios/Config/Local.xcconfig)
#   SKIP_UPLOAD=1                   run `fastlane build_release` only
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCAL_XC="$ROOT/ios/Config/Local.xcconfig"

read_xcconfig() {
  local key="$1"
  grep -E "^${key}[[:space:]]*=" "$LOCAL_XC" 2>/dev/null | head -1 \
    | sed -E "s/^${key}[[:space:]]*=[[:space:]]*//" | tr -d '[:space:]' || true
}

if [[ ! -f "$LOCAL_XC" ]]; then
  echo "Missing $LOCAL_XC — copy from Local.xcconfig.example" >&2
  exit 1
fi

if [[ -z "${IOS_DEVELOPMENT_TEAM:-}" ]]; then
  IOS_DEVELOPMENT_TEAM="$(read_xcconfig DEVELOPMENT_TEAM)"
fi
export IOS_DEVELOPMENT_TEAM
[[ -n "$IOS_DEVELOPMENT_TEAM" ]] || { echo "Set DEVELOPMENT_TEAM in Local.xcconfig" >&2; exit 1; }

if [[ -z "${APP_STORE_CONNECT_API_KEY_PATH:-}" ]]; then
  shopt -s nullglob
  keys=( "$HOME"/Keys/morning-guard/AuthKey_*.p8 )
  if [[ ${#keys[@]} -eq 1 ]]; then
    APP_STORE_CONNECT_API_KEY_PATH="${keys[0]}"
  else
    echo "Set APP_STORE_CONNECT_API_KEY_PATH to your .p8 file" >&2
    exit 1
  fi
fi
[[ -f "$APP_STORE_CONNECT_API_KEY_PATH" ]] || { echo "Key not found: $APP_STORE_CONNECT_API_KEY_PATH" >&2; exit 1; }

if [[ -z "${APP_STORE_CONNECT_API_KEY_ID:-}" ]]; then
  APP_STORE_CONNECT_API_KEY_ID="$(read_xcconfig APP_STORE_CONNECT_API_KEY_ID)"
fi
if [[ -z "${APP_STORE_CONNECT_API_KEY_ID:-}" ]]; then
  base="$(basename "$APP_STORE_CONNECT_API_KEY_PATH")"
  if [[ "$base" =~ ^AuthKey_(.+)\.p8$ ]]; then
    APP_STORE_CONNECT_API_KEY_ID="${BASH_REMATCH[1]}"
  fi
fi
[[ -n "${APP_STORE_CONNECT_API_KEY_ID:-}" ]] || { echo "Set APP_STORE_CONNECT_API_KEY_ID" >&2; exit 1; }
if [[ -z "${APP_STORE_CONNECT_API_ISSUER_ID:-}" ]]; then
  APP_STORE_CONNECT_API_ISSUER_ID="$(read_xcconfig APP_STORE_CONNECT_API_ISSUER_ID)"
fi
[[ -n "${APP_STORE_CONNECT_API_ISSUER_ID:-}" ]] || {
  echo "Add APP_STORE_CONNECT_API_ISSUER_ID to ios/Config/Local.xcconfig (see Local.xcconfig.example)" >&2
  exit 1
}

export APP_STORE_CONNECT_API_KEY_PATH
export APP_STORE_CONNECT_API_KEY_ID
export APP_STORE_CONNECT_API_ISSUER_ID
export APP_STORE_CONNECT_API_KEY="$(base64 <"$APP_STORE_CONNECT_API_KEY_PATH" | tr -d '\n')"

# Local archives must use the Xcode login session, not CI/API-key xcodebuild auth.
unset CI
unset USE_ASC_API_XCODE_AUTH

echo "Team: $IOS_DEVELOPMENT_TEAM  Key ID: $APP_STORE_CONNECT_API_KEY_ID"
echo "Using key: $APP_STORE_CONNECT_API_KEY_PATH"
echo "Archive signing: Xcode login session (set USE_ASC_API_XCODE_AUTH=1 to force API-key xcodebuild auth)."

"$ROOT/scripts/sync-ios-version.sh"
(cd "$ROOT/ios" && ./scripts/generate-xcodeproj.sh)
cd "$ROOT/ios"
bundle install --quiet 2>/dev/null || bundle install

if [[ "${SKIP_UPLOAD:-}" == "1" ]]; then
  bundle exec fastlane build_release
else
  bundle exec fastlane beta
fi
