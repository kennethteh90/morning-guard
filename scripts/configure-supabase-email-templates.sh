#!/usr/bin/env bash
# Apply Morning Guard branded auth email templates to a hosted Supabase project.
# Requires a personal access token: https://supabase.com/dashboard/account/tokens
#
# Usage:
#   export SUPABASE_ACCESS_TOKEN=sbp_...
#   ./scripts/configure-supabase-email-templates.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REF="${SUPABASE_PROJECT_REF:-otabcjkhqcpbstssinxe}"
TEMPLATE="${ROOT}/supabase/templates/magic_link.html"
SUBJECT="${SUPABASE_MAGIC_LINK_SUBJECT:-Sign in to Morning Guard}"

if [[ -z "${SUPABASE_ACCESS_TOKEN:-}" ]]; then
  echo "Set SUPABASE_ACCESS_TOKEN (Supabase dashboard → Account → Access tokens)" >&2
  exit 1
fi
if [[ ! -f "$TEMPLATE" ]]; then
  echo "Missing template: $TEMPLATE" >&2
  exit 1
fi

BODY=$(python3 - "$TEMPLATE" "$SUBJECT" <<'PY'
import json
import sys
from pathlib import Path

content = Path(sys.argv[1]).read_text(encoding="utf-8")
subject = sys.argv[2]
print(
    json.dumps(
        {
            "mailer_subjects_magic_link": subject,
            "mailer_templates_magic_link_content": content,
        }
    )
)
PY
)

RESP=$(curl -sS -X PATCH "https://api.supabase.com/v1/projects/${REF}/config/auth" \
  -H "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$BODY")
echo "$RESP" | python3 -m json.tool 2>/dev/null || echo "$RESP"

if echo "$RESP" | grep -q "not available for free tier"; then
  echo >&2
  echo "Templates need custom SMTP or a paid plan. See docs/supabase/SETUP.md §4." >&2
  exit 1
fi

echo "Updated magic-link email template for project ${REF}"
