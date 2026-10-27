#!/usr/bin/env bash
# Non-destructive checklist for iOS device / TestFlight readiness.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="kennethteh90/morning-guard"
PROPS="$ROOT/local.properties"
LOCAL_XC="$ROOT/ios/Config/Local.xcconfig"

ok=0
warn=0
fail=0

pass() { echo "  OK   $1"; ok=$((ok + 1)); }
note() { echo "  WARN $1"; warn=$((warn + 1)); }
bad()  { echo "  FAIL $1"; fail=$((fail + 1)); }

get_prop() {
  local key="$1"
  grep -E "^${key}=" "$PROPS" 2>/dev/null | head -1 | cut -d= -f2- || true
}

xcconfig_set() {
  local key="$1"
  [[ -f "$LOCAL_XC" ]] && grep -qE "^${key}\s*=" "$LOCAL_XC" 2>/dev/null
}

echo "=== Local Mac / Xcode ==="
if [[ -x /Applications/Xcode.app/Contents/Developer/usr/bin/xcodebuild ]]; then
  pass "Xcode installed"
else
  bad "Xcode.app not found"
fi
if command -v xcodegen >/dev/null 2>&1; then
  pass "xcodegen on PATH"
else
  note "Install xcodegen: brew install xcodegen"
fi
if [[ -f "$LOCAL_XC" ]]; then
  pass "ios/Config/Local.xcconfig exists"
  if xcconfig_set DEVELOPMENT_TEAM; then pass "DEVELOPMENT_TEAM in Local.xcconfig"; else bad "Set DEVELOPMENT_TEAM in Local.xcconfig"; fi
  if xcconfig_set SUPABASE_URL && xcconfig_set SUPABASE_ANON_KEY; then pass "Supabase keys in Local.xcconfig"; else note "Supabase optional for local-only; set for cloud sync builds"; fi
  if xcconfig_set BIBLE_PROXY_BASE_URL; then pass "BIBLE_PROXY_BASE_URL in Local.xcconfig"; else note "Set proxy URL for Bible chapters on device"; fi
else
  bad "Copy ios/Config/Local.xcconfig.example → Local.xcconfig"
fi

echo ""
echo "=== Repo / versions ==="
if [[ -f "$ROOT/version.properties" ]]; then
  pass "version.properties present ($(grep versionMajor "$ROOT/version.properties" | tr -d '\r'), …)"
else
  bad "version.properties missing"
fi
if grep -q 'com.apple.developer.family-controls' "$ROOT/ios/MorningGuard/MorningGuard.entitlements"; then
  pass "Family Controls entitlement in app plist"
else
  bad "Missing family-controls entitlement"
fi

echo ""
echo "=== GitHub Actions (TestFlight CI) ==="
if command -v gh >/dev/null 2>&1 && gh auth status --hostname github.com >/dev/null 2>&1; then
  for name in IOS_DEVELOPMENT_TEAM APP_STORE_CONNECT_API_KEY_ID APP_STORE_CONNECT_API_ISSUER_ID APP_STORE_CONNECT_API_KEY; do
    if gh secret list --repo "$REPO" 2>/dev/null | awk '{print $1}' | grep -qx "$name"; then
      pass "GitHub secret $name"
    else
      bad "GitHub secret $name (run ./scripts/push-ios-github-secrets.sh after creating ASC API key)"
    fi
  done
  for name in IOS_DISTRIBUTION_CERTIFICATE_P12 IOS_DISTRIBUTION_CERTIFICATE_PASSWORD; do
    if gh secret list --repo "$REPO" 2>/dev/null | awk '{print $1}' | grep -qx "$name"; then
      pass "GitHub secret $name"
    else
      bad "GitHub secret $name (run ./scripts/push-ios-distribution-cert-secret.sh)"
    fi
  done
  for name in SUPABASE_URL SUPABASE_ANON_KEY BIBLE_PROXY_BASE_URL; do
    if gh secret list --repo "$REPO" 2>/dev/null | awk '{print $1}' | grep -qx "$name"; then
      pass "GitHub secret $name (shared with Android)"
    else
      note "GitHub secret $name missing — push via push-github-secrets.sh or push-ios-github-secrets.sh"
    fi
  done
else
  note "gh not authenticated — skip GitHub secret checks"
fi

echo ""
echo "=== Cloudflare bible-cache worker ==="
if command -v npx >/dev/null 2>&1 && [[ -d "$ROOT/workers/bible-cache" ]]; then
  if (cd "$ROOT/workers/bible-cache" && npx wrangler secret list --name morning-guard-bible-cache 2>/dev/null | grep -q APPLE_TEAM_ID); then
    pass "Worker secret APPLE_TEAM_ID"
  else
    bad "Worker secret APPLE_TEAM_ID (wrangler secret put APPLE_TEAM_ID)"
  fi
else
  note "Could not check worker secrets (wrangler/npx)"
fi

echo ""
echo "=== Manual (cannot verify from repo) ==="
note "App Store Connect app record created for com.morningguard.app"
note "Family Controls distribution entitlement approved by Apple (required for TestFlight shields)"
note "Agreements accepted in developer.apple.com + App Store Connect"
note "First device install: ./ios/scripts/open-for-device.sh → trust cert on iPhone"

echo ""
echo "Summary: $ok passed, $warn warnings, $fail failures"
if [[ "$fail" -gt 0 ]]; then
  exit 1
fi
