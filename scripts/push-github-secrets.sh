#!/usr/bin/env bash
# Push build secrets from local.properties to GitHub Actions (repo: morning-guard).
# Usage: ./scripts/push-github-secrets.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="$ROOT/local.properties"
if [[ ! -f "$PROPS" ]]; then
  echo "Missing local.properties — copy from local.properties.example" >&2
  exit 1
fi
get_prop() {
  grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2- || true
}
push_if_set() {
  local name="$1"
  local value
  value="$(get_prop "$name")"
  if [[ -n "$value" ]]; then
    echo "Setting secret $name"
    gh secret set "$name" --body "$value"
  else
    echo "Skipping $name (empty)"
  fi
}
for key in SUPABASE_URL SUPABASE_ANON_KEY SENTRY_DSN GOOGLE_WEB_CLIENT_ID BIBLE_PROXY_BASE_URL \
  PLAY_CLOUD_PROJECT_NUMBER PRIVACY_POLICY_URL RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
  push_if_set "$key"
done

ks="$(get_prop RELEASE_STORE_FILE)"
if [[ -n "$ks" ]]; then
  [[ "$ks" != /* ]] && ks="$ROOT/$ks"
  if [[ -f "$ks" ]]; then
    echo "Setting secret RELEASE_KEYSTORE_BASE64 (from RELEASE_STORE_FILE)"
    gh secret set RELEASE_KEYSTORE_BASE64 --body "$(base64 < "$ks" | tr -d '\n')"
  else
    echo "Skipping RELEASE_KEYSTORE_BASE64 (RELEASE_STORE_FILE not found: $ks)" >&2
  fi
else
  echo "Skipping RELEASE_KEYSTORE_BASE64 (RELEASE_STORE_FILE empty)"
fi

echo "Done. Verify with: gh secret list"
