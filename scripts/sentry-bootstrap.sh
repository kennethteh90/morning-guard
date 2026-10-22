#!/usr/bin/env bash
# Create (or reuse) the Morning Guard Android project in Sentry and print the DSN.
set -euo pipefail

ORG="${SENTRY_ORG:-kenneth-t3}"
PROJECT_SLUG="${SENTRY_PROJECT_SLUG:-morning-guard}"
PROJECT_NAME="${SENTRY_PROJECT_NAME:-Morning Guard}"
TEAM_SLUG="${SENTRY_TEAM_SLUG:-}"
API="https://sentry.io/api/0"

if [[ -z "${SENTRY_AUTH_TOKEN:-}" ]]; then
  echo "Set SENTRY_AUTH_TOKEN (org token with project:write)." >&2
  echo "Create one: https://sentry.io/settings/account/api/auth-tokens/" >&2
  exit 1
fi

auth=(-H "Authorization: Bearer ${SENTRY_AUTH_TOKEN}" -H "Content-Type: application/json")

echo "Checking organization: ${ORG}"
org_check="$(curl -sf "${API}/organizations/${ORG}/" "${auth[@]}" || true)"
if [[ -z "${org_check}" ]]; then
  echo "Could not read org '${ORG}'. Check SENTRY_AUTH_TOKEN and org slug." >&2
  exit 1
fi

if [[ -z "${TEAM_SLUG}" ]]; then
  echo "Listing teams (using first team if SENTRY_TEAM_SLUG not set)…"
  teams_json="$(curl -sf "${API}/organizations/${ORG}/teams/" "${auth[@]}")"
  TEAM_SLUG="$(echo "${teams_json}" | python3 -c 'import json,sys; teams=json.load(sys.stdin); assert teams; print(teams[0]["slug"])')"
  echo "Using team slug: ${TEAM_SLUG}"
fi

existing="$(curl -sf "${API}/projects/${ORG}/${PROJECT_SLUG}/" "${auth[@]}" 2>/dev/null || true)"
if [[ -n "${existing}" ]]; then
  echo "Project already exists: ${ORG}/${PROJECT_SLUG}"
else
  echo "Creating Android project ${PROJECT_SLUG}…"
  curl -sf "${API}/teams/${ORG}/${TEAM_SLUG}/projects/" "${auth[@]}" \
    -d "$(python3 - <<PY
import json
print(json.dumps({
  "name": "${PROJECT_NAME}",
  "slug": "${PROJECT_SLUG}",
  "platform": "android",
  "default_rules": True,
}))
PY
)" >/dev/null
  echo "Created ${ORG}/${PROJECT_SLUG}"
fi

keys_json="$(curl -sf "${API}/projects/${ORG}/${PROJECT_SLUG}/keys/" "${auth[@]}")"
DSN="$(echo "${keys_json}" | python3 -c 'import json,sys; keys=json.load(sys.stdin); assert keys; print(keys[0]["dsn"]["public"])')"

echo ""
echo "=== Morning Guard Sentry DSN ==="
echo "${DSN}"
echo ""
echo "Add to local.properties:"
echo "SENTRY_DSN=${DSN}"
echo ""
echo "GitHub Actions secret:"
echo "  gh secret set SENTRY_DSN --repo kennethteh90/morning-guard"
echo "(paste the DSN when prompted)"
echo ""
echo "Project URL: https://sentry.io/organizations/${ORG}/projects/${PROJECT_SLUG}/"
