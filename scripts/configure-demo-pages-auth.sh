#!/usr/bin/env bash
# Set HTTP Basic Auth secrets for https://morning-guard.codingagents.cc/demo/
# Usage:
#   DEMO_BASIC_AUTH_USER=demo DEMO_BASIC_AUTH_PASSWORD='your-secret' ./scripts/configure-demo-pages-auth.sh
set -euo pipefail
PROJECT="${PAGES_PROJECT_NAME:-morning-guard-auth}"
USER="${DEMO_BASIC_AUTH_USER:-}"
PASS="${DEMO_BASIC_AUTH_PASSWORD:-}"

if [[ -z "$USER" || -z "$PASS" ]]; then
  echo "Set DEMO_BASIC_AUTH_USER and DEMO_BASIC_AUTH_PASSWORD in the environment." >&2
  echo "Example: DEMO_BASIC_AUTH_USER=demo DEMO_BASIC_AUTH_PASSWORD=\$(openssl rand -base64 18) $0" >&2
  exit 1
fi

if [[ ${#PASS} -lt 8 ]]; then
  echo "Use a password of at least 8 characters." >&2
  exit 1
fi

echo "Updating Pages secrets on project: $PROJECT"
printf '%s' "$USER" | npx wrangler pages secret put DEMO_BASIC_AUTH_USER --project-name="$PROJECT"
printf '%s' "$PASS" | npx wrangler pages secret put DEMO_BASIC_AUTH_PASSWORD --project-name="$PROJECT"
echo "Done. Demo URL: https://morning-guard.codingagents.cc/demo/"
echo "Username: $USER"
