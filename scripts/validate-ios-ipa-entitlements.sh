#!/usr/bin/env bash
# Fail if the main app binary in an IPA is not signed with Family Controls (Screen Time auth breaks).
set -euo pipefail

IPA="${1:-}"
if [[ -z "$IPA" || ! -f "$IPA" ]]; then
  echo "Usage: $0 path/to/MorningGuard.ipa" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
unzip -q -o "$IPA" -d "$TMP"

APP_BIN="$(find "$TMP" -path '*/MorningGuard.app/MorningGuard' -type f | head -1)"
if [[ -z "$APP_BIN" ]]; then
  echo "FAIL: MorningGuard binary not found in $IPA" >&2
  exit 1
fi

ENT="$(mktemp)"
codesign -d --entitlements - "$APP_BIN" >"$ENT" 2>/dev/null || true

if ! grep -q 'com.apple.developer.family-controls' "$ENT"; then
  echo "FAIL: Main app is missing com.apple.developer.family-controls in its signature." >&2
  echo "Screen Time authorization will show \"Couldn't communicate with a helper application.\"" >&2
  echo "Regenerate the Xcode project (xcodegen) and ensure Release does not set CODE_SIGN_IDENTITY to empty." >&2
  exit 1
fi

echo "OK: Main app signature includes Family Controls."
