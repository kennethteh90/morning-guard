#!/usr/bin/env bash
# Fail fast when xcconfig URL keys use https:// — the // starts an xcconfig comment,
# so the built Info.plist ends up with values like "https:" and Supabase can crash at launch.
set -euo pipefail

file="${1:-}"
if [[ -z "$file" || ! -f "$file" ]]; then
  echo "Usage: $0 <path-to-Local.xcconfig-or-CI.xcconfig>" >&2
  exit 1
fi

bad=0
while IFS= read -r line; do
  [[ "$line" =~ ^[[:space:]]*# ]] && continue
  [[ "$line" =~ ^[[:space:]]*// ]] && continue
  if [[ "$line" =~ ^[[:space:]]*(SUPABASE_URL|BIBLE_PROXY_BASE_URL|SENTRY_DSN)[[:space:]]*=[[:space:]]*https:// ]]; then
    echo "Invalid URL in $file (xcconfig treats // as a comment):" >&2
    echo "  $line" >&2
    echo "Use https:/\$()/host/path — see ios/Config/Local.xcconfig.example" >&2
    bad=1
  fi
done <"$file"

if [[ "$bad" -ne 0 ]]; then
  exit 1
fi
