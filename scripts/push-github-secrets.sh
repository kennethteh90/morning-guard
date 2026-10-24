#!/usr/bin/env bash
# Push Android release build secrets from local.properties to GitHub Actions.
# Usage (from repo root):
#   ./scripts/push-github-secrets.sh
#   ./scripts/push-github-secrets.sh --dry-run
#   ./scripts/push-github-secrets.sh --play /path/to/play-service-account.json
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="kennethteh90/morning-guard"
PROPS="$ROOT/local.properties"

DRY_RUN=false
PLAY_JSON_PATH=""

usage() {
  cat <<'EOF'
Usage: ./scripts/push-github-secrets.sh [--dry-run] [--play PATH]

  --dry-run   List secret names that would be set; report missing required keys (no values).
  --play PATH Also set PLAY_SERVICE_ACCOUNT_JSON from the given JSON key file.

Reads local.properties (see local.properties.example). Keystore bytes go to ANDROID_KEYSTORE_B64.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --play)
      [[ $# -ge 2 ]] || { echo "Missing path after --play" >&2; exit 1; }
      PLAY_JSON_PATH="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "$PROPS" ]]; then
  echo "Missing local.properties — copy from local.properties.example" >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is not installed. Install from https://cli.github.com/" >&2
  exit 1
fi

if ! gh auth status --hostname github.com >/dev/null 2>&1; then
  echo "gh is not authenticated. Run: gh auth login" >&2
  exit 1
fi

get_prop() {
  local key="$1"
  grep -E "^${key}=" "$PROPS" 2>/dev/null | head -1 | cut -d= -f2- || true
}

# Keys read by app/build.gradle.kts prop() for release builds (excluding RELEASE_STORE_FILE path).
PROP_SECRETS=(
  SUPABASE_URL
  SUPABASE_ANON_KEY
  SENTRY_DSN
  GOOGLE_WEB_CLIENT_ID
  BIBLE_PROXY_BASE_URL
  PLAY_CLOUD_PROJECT_NUMBER
  PRIVACY_POLICY_URL
  RELEASE_STORE_PASSWORD
  RELEASE_KEY_ALIAS
  RELEASE_KEY_PASSWORD
)

REQUIRED_PROPS=(
  SUPABASE_URL
  SUPABASE_ANON_KEY
  BIBLE_PROXY_BASE_URL
  PLAY_CLOUD_PROJECT_NUMBER
  PRIVACY_POLICY_URL
  RELEASE_STORE_PASSWORD
  RELEASE_KEY_ALIAS
  RELEASE_KEY_PASSWORD
)

missing_required=()
for key in "${REQUIRED_PROPS[@]}"; do
  if [[ -z "$(get_prop "$key")" ]]; then
    missing_required+=("$key")
  fi
done

ks_path="$(get_prop RELEASE_STORE_FILE)"
if [[ -z "$ks_path" ]]; then
  missing_required+=("RELEASE_STORE_FILE")
elif [[ "$ks_path" != /* ]]; then
  ks_path="$ROOT/$ks_path"
fi
if [[ -n "$ks_path" && ! -f "$ks_path" ]]; then
  echo "RELEASE_STORE_FILE does not exist: $ks_path" >&2
  missing_required+=("RELEASE_STORE_FILE (file missing)")
fi

if [[ -n "$PLAY_JSON_PATH" && ! -f "$PLAY_JSON_PATH" ]]; then
  echo "Play service account file not found: $PLAY_JSON_PATH" >&2
  exit 1
fi

secret_names=("${PROP_SECRETS[@]}" ANDROID_KEYSTORE_B64)
if [[ -n "$PLAY_JSON_PATH" ]]; then
  secret_names+=(PLAY_SERVICE_ACCOUNT_JSON)
fi

if [[ "$DRY_RUN" == true ]]; then
  echo "Repository: $REPO"
  echo "Secrets that would be set (names only):"
  for name in "${secret_names[@]}"; do
    case "$name" in
      ANDROID_KEYSTORE_B64)
        if [[ -n "$ks_path" && -f "$ks_path" ]]; then
          echo "  ANDROID_KEYSTORE_B64"
        else
          echo "  ANDROID_KEYSTORE_B64 (missing — need RELEASE_STORE_FILE)"
        fi
        ;;
      PLAY_SERVICE_ACCOUNT_JSON)
        echo "  PLAY_SERVICE_ACCOUNT_JSON"
        ;;
      *)
        if [[ -n "$(get_prop "$name")" ]]; then
          echo "  $name"
        else
          echo "  $name (empty in local.properties)"
        fi
        ;;
    esac
  done
  if [[ ${#missing_required[@]} -gt 0 ]]; then
    echo ""
    echo "Missing required local.properties keys:"
    printf '  - %s\n' "${missing_required[@]}"
    exit 1
  fi
  echo ""
  echo "Dry run complete (no secrets were sent)."
  exit 0
fi

if [[ ${#missing_required[@]} -gt 0 ]]; then
  echo "Missing required local.properties keys:" >&2
  printf '  - %s\n' "${missing_required[@]}" >&2
  exit 1
fi

push_secret() {
  local name="$1"
  local value="$2"
  printf '%s' "$value" | gh secret set "$name" --repo "$REPO"
  echo "Set secret $name"
}

for key in "${PROP_SECRETS[@]}"; do
  value="$(get_prop "$key")"
  if [[ -n "$value" ]]; then
    push_secret "$key" "$value"
  else
    echo "Skipping $key (empty in local.properties)"
  fi
done

if [[ -n "$ks_path" && -f "$ks_path" ]]; then
  push_secret "ANDROID_KEYSTORE_B64" "$(base64 < "$ks_path" | tr -d '\n')"
else
  echo "Cannot set ANDROID_KEYSTORE_B64 (keystore file missing)" >&2
  exit 1
fi

if [[ -n "$PLAY_JSON_PATH" ]]; then
  push_secret "PLAY_SERVICE_ACCOUNT_JSON" "$(<"$PLAY_JSON_PATH")"
fi

echo "Done. Verify with: gh secret list --repo $REPO"
