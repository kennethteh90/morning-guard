#!/usr/bin/env bash
# Attach morning-guard.codingagents.cc to Cloudflare Pages project morning-guard-auth.
# Uses wrangler OAuth token (run wrangler login first).
set -euo pipefail
DOMAIN="${PAGES_CUSTOM_DOMAIN:-morning-guard.codingagents.cc}"
PROJECT="${PAGES_PROJECT_NAME:-morning-guard-auth}"
ACCOUNT="${CLOUDFLARE_ACCOUNT_ID:-2891d2e2a42534e52f0b79b56622c09b}"
WRANGLER_CONFIG="${WRANGLER_CONFIG:-$HOME/Library/Preferences/.wrangler/config/default.toml}"
if [[ ! -f "$WRANGLER_CONFIG" ]]; then
  echo "Run: npx wrangler login" >&2
  exit 1
fi
TOKEN=$(grep '^oauth_token' "$WRANGLER_CONFIG" | sed 's/oauth_token = "\(.*\)"/\1/')
curl -sS -X POST "https://api.cloudflare.com/client/v4/accounts/${ACCOUNT}/pages/projects/${PROJECT}/domains" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${DOMAIN}\"}" | python3 -m json.tool
