#!/usr/bin/env bash
# Set Supabase Auth Site URL + redirect allow list for Morning Guard.
# Requires a personal access token: https://supabase.com/dashboard/account/tokens
#
# Usage:
#   export SUPABASE_ACCESS_TOKEN=sbp_...
#   ./scripts/configure-supabase-auth-urls.sh
set -euo pipefail
REF="${SUPABASE_PROJECT_REF:-otabcjkhqcpbstssinxe}"
SITE_URL="${SUPABASE_SITE_URL:-https://morning-guard.codingagents.cc}"
ALLOW_LIST="${SUPABASE_URI_ALLOW_LIST:-morningguard://auth,https://morning-guard.codingagents.cc,https://morning-guard.codingagents.cc/**}"
if [[ -z "${SUPABASE_ACCESS_TOKEN:-}" ]]; then
  echo "Set SUPABASE_ACCESS_TOKEN (Supabase dashboard → Account → Access tokens)" >&2
  exit 1
fi
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "site_url": "$SITE_URL",
  "uri_allow_list": "$ALLOW_LIST",
}))
PY
)
curl -sS -X PATCH "https://api.supabase.com/v1/projects/${REF}/config/auth" \
  -H "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$BODY" | python3 -m json.tool
echo "Updated auth config for project ${REF}"
