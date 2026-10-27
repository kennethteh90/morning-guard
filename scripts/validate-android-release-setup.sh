#!/usr/bin/env bash
# Non-destructive checklist for Android Play / CI release secrets.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="kennethteh90/morning-guard"
PROPS="$ROOT/local.properties"

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

echo "=== Local Android signing (local.properties) ==="
if [[ -f "$PROPS" ]]; then
  pass "local.properties exists"
  for key in RELEASE_STORE_FILE RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
    if [[ -n "$(get_prop "$key")" ]]; then
      pass "$key set"
    else
      bad "Set $key in local.properties"
    fi
  done
  ks="$(get_prop RELEASE_STORE_FILE)"
  if [[ -n "$ks" ]]; then
    [[ "$ks" != /* ]] && ks="$ROOT/$ks"
    if [[ -f "$ks" ]]; then
      pass "RELEASE_STORE_FILE points to existing keystore"
    else
      bad "Keystore missing at $ks"
    fi
  fi
else
  bad "Copy local.properties.example → local.properties"
fi

echo ""
echo "=== GitHub Actions (Android Release workflow) ==="
if command -v gh >/dev/null 2>&1 && gh auth status --hostname github.com >/dev/null 2>&1; then
  for name in ANDROID_KEYSTORE_B64 RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD \
    PRIVACY_POLICY_URL SUPABASE_URL SUPABASE_ANON_KEY BIBLE_PROXY_BASE_URL PLAY_CLOUD_PROJECT_NUMBER; do
    if gh secret list --repo "$REPO" 2>/dev/null | awk '{print $1}' | grep -qx "$name"; then
      pass "GitHub secret $name"
    else
      bad "GitHub secret $name (run ./scripts/push-github-secrets.sh)"
    fi
  done
  for name in GOOGLE_WEB_CLIENT_ID SENTRY_DSN; do
    if gh secret list --repo "$REPO" 2>/dev/null | awk '{print $1}' | grep -qx "$name"; then
      pass "GitHub secret $name"
    else
      note "GitHub secret $name missing (optional for some builds)"
    fi
  done
  if gh secret list --repo "$REPO" 2>/dev/null | awk '{print $1}' | grep -qx "PLAY_SERVICE_ACCOUNT_JSON"; then
    pass "GitHub secret PLAY_SERVICE_ACCOUNT_JSON"
  else
    note "PLAY_SERVICE_ACCOUNT_JSON missing — release.yml skips Play upload"
  fi
  if gh secret list --repo "$REPO" 2>/dev/null | awk '{print $1}' | grep -qx "RELEASE_KEYSTORE_BASE64"; then
    note "Legacy secret RELEASE_KEYSTORE_BASE64 still set (unused; safe to delete)"
  fi
  if gh secret list --repo "$REPO" 2>/dev/null | awk '{print $1}' | grep -qx "RELEASE_STORE_FILE"; then
    note "Legacy secret RELEASE_STORE_FILE still set (CI uses ANDROID_KEYSTORE_B64; safe to delete)"
  fi
else
  note "gh not authenticated — skip GitHub secret checks"
fi

echo ""
echo "Summary: $ok passed, $warn warnings, $fail failures"
if [[ "$fail" -gt 0 ]]; then
  exit 1
fi
