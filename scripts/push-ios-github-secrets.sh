#!/usr/bin/env bash
# Push iOS TestFlight / CI secrets to GitHub Actions.
#
# Usage (from repo root):
#   export APP_STORE_CONNECT_API_KEY_ID=...
#   export APP_STORE_CONNECT_API_ISSUER_ID=...
#   export APP_STORE_CONNECT_API_KEY_PATH=~/path/AuthKey_XXXX.p8
#   ./scripts/push-ios-github-secrets.sh
#   ./scripts/push-ios-github-secrets.sh --dry-run
#
# Reuses SUPABASE_*, BIBLE_PROXY_BASE_URL, SENTRY_DSN from local.properties when set.
# Reads IOS_DEVELOPMENT_TEAM from ios/Config/Local.xcconfig (DEVELOPMENT_TEAM) when not exported.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="kennethteh90/morning-guard"
PROPS="$ROOT/local.properties"
LOCAL_XC="$ROOT/ios/Config/Local.xcconfig"

DRY_RUN=false

usage() {
  cat <<'EOF'
Usage: ./scripts/push-ios-github-secrets.sh [--dry-run]

Environment (App Store Connect API — create in Users and Access → Keys):
  APP_STORE_CONNECT_API_KEY_ID
  APP_STORE_CONNECT_API_ISSUER_ID
  APP_STORE_CONNECT_API_KEY_PATH   path to .p8 (contents → APP_STORE_CONNECT_API_KEY, base64)

Optional:
  IOS_DEVELOPMENT_TEAM             defaults to DEVELOPMENT_TEAM in ios/Config/Local.xcconfig

Also sets (from local.properties if present): SUPABASE_URL, SUPABASE_ANON_KEY,
BIBLE_PROXY_BASE_URL, SENTRY_DSN — same names as Android CI when missing on GitHub.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
done

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required." >&2
  exit 1
fi

get_prop() {
  local key="$1"
  grep -E "^${key}=" "$PROPS" 2>/dev/null | head -1 | cut -d= -f2- || true
}

get_xcconfig_team() {
  if [[ ! -f "$LOCAL_XC" ]]; then
    return 0
  fi
  grep -E '^DEVELOPMENT_TEAM\s*=' "$LOCAL_XC" 2>/dev/null | head -1 | sed -E 's/^DEVELOPMENT_TEAM\s*=\s*//' | tr -d ' ' || true
}

IOS_DEVELOPMENT_TEAM="${IOS_DEVELOPMENT_TEAM:-$(get_xcconfig_team)}"

missing=()
[[ -n "${APP_STORE_CONNECT_API_KEY_ID:-}" ]] || missing+=("APP_STORE_CONNECT_API_KEY_ID")
[[ -n "${APP_STORE_CONNECT_API_ISSUER_ID:-}" ]] || missing+=("APP_STORE_CONNECT_API_ISSUER_ID")
[[ -n "${APP_STORE_CONNECT_API_KEY_PATH:-}" && -f "${APP_STORE_CONNECT_API_KEY_PATH}" ]] || missing+=("APP_STORE_CONNECT_API_KEY_PATH (readable file)")
[[ -n "$IOS_DEVELOPMENT_TEAM" ]] || missing+=("IOS_DEVELOPMENT_TEAM or DEVELOPMENT_TEAM in Local.xcconfig")

secret_names=(
  IOS_DEVELOPMENT_TEAM
  APP_STORE_CONNECT_API_KEY_ID
  APP_STORE_CONNECT_API_ISSUER_ID
  APP_STORE_CONNECT_API_KEY
)

for key in SUPABASE_URL SUPABASE_ANON_KEY BIBLE_PROXY_BASE_URL SENTRY_DSN; do
  if [[ -n "$(get_prop "$key")" ]]; then
    secret_names+=("$key")
  fi
done

if [[ "$DRY_RUN" == true ]]; then
  echo "Repository: $REPO"
  echo "Secrets that would be set:"
  for name in "${secret_names[@]}"; do
    echo "  $name"
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo ""
    echo "Missing:"
    printf '  - %s\n' "${missing[@]}"
    exit 1
  fi
  echo ""
  echo "Dry run complete."
  exit 0
fi

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "Missing required inputs:" >&2
  printf '  - %s\n' "${missing[@]}" >&2
  exit 1
fi

push_secret() {
  local name="$1"
  local value="$2"
  printf '%s' "$value" | gh secret set "$name" --repo "$REPO"
  echo "Set secret $name"
}

push_secret IOS_DEVELOPMENT_TEAM "$IOS_DEVELOPMENT_TEAM"
push_secret APP_STORE_CONNECT_API_KEY_ID "$APP_STORE_CONNECT_API_KEY_ID"
push_secret APP_STORE_CONNECT_API_ISSUER_ID "$APP_STORE_CONNECT_API_ISSUER_ID"
push_secret APP_STORE_CONNECT_API_KEY "$(base64 <"$APP_STORE_CONNECT_API_KEY_PATH" | tr -d '\n')"

for key in SUPABASE_URL SUPABASE_ANON_KEY BIBLE_PROXY_BASE_URL SENTRY_DSN; do
  value="$(get_prop "$key")"
  if [[ -n "$value" ]]; then
    push_secret "$key" "$value"
  fi
done

echo "Done. Verify: gh secret list --repo $REPO"
