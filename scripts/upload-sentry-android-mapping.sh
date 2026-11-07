#!/usr/bin/env bash
# Upload Android R8 mapping + optional native symbols to Sentry for a release build.
# Requires: SENTRY_AUTH_TOKEN, SENTRY_ORG (default kenneth-t3), SENTRY_PROJECT (default morning-guard)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -z "${SENTRY_AUTH_TOKEN:-}" ]]; then
  echo "SENTRY_AUTH_TOKEN is not set; skipping Sentry ProGuard upload."
  exit 0
fi

ORG="${SENTRY_ORG:-kenneth-t3}"
PROJECT="${SENTRY_PROJECT:-morning-guard}"
eval "$(grep -E '^version(Major|Minor|Patch)=' version.properties)"
VERSION="${versionMajor}.${versionMinor}.${versionPatch}"
RELEASE="com.morningguard.app@${VERSION}"
DIST="$((versionMajor * 10000 + versionMinor * 100 + versionPatch))"
MAPPING="${1:-app/build/outputs/mapping/fullRelease/mapping.txt}"

if [[ ! -f "$MAPPING" ]]; then
  echo "No R8 mapping at $MAPPING; skipping Sentry upload."
  exit 0
fi

CLI="${RUNNER_TEMP:-/tmp}/sentry-cli"
if [[ ! -x "$CLI" ]]; then
  curl -sL "https://github.com/getsentry/sentry-cli/releases/download/2.39.1/sentry-cli-Linux-x86_64" -o "$CLI"
  chmod +x "$CLI"
fi

"$CLI" releases new "$RELEASE" --org "$ORG" --project "$PROJECT" || true
"$CLI" releases upload-proguard "$RELEASE" \
  --org "$ORG" \
  --project "$PROJECT" \
  "$MAPPING"
"$CLI" releases finalize "$RELEASE" --org "$ORG" --project "$PROJECT" || true
"$CLI" releases set-commits "$RELEASE" --auto --org "$ORG" --project "$PROJECT" || true

echo "Uploaded ProGuard mapping for release=$RELEASE dist=$DIST"
