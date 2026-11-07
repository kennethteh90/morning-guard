#!/usr/bin/env bash
# Upload iOS dSYM bundles from an Xcode archive to Sentry.
# Requires: SENTRY_AUTH_TOKEN, SENTRY_ORG (default kenneth-t3), SENTRY_PROJECT (default morning-guard)
set -euo pipefail

ARCHIVE_PATH="${1:-}"
if [[ -z "$ARCHIVE_PATH" || ! -d "$ARCHIVE_PATH" ]]; then
  echo "Usage: $0 path/to/MorningGuard.xcarchive" >&2
  exit 1
fi

if [[ -z "${SENTRY_AUTH_TOKEN:-}" ]]; then
  echo "SENTRY_AUTH_TOKEN is not set; skipping Sentry dSYM upload."
  exit 0
fi

ORG="${SENTRY_ORG:-kenneth-t3}"
PROJECT="${SENTRY_PROJECT:-morning-guard}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
eval "$(grep -E '^version(Major|Minor|Patch)=' "$ROOT/version.properties")"
VERSION="${versionMajor}.${versionMinor}.${versionPatch}"
RELEASE="com.morningguard.app@${VERSION}"

CLI="${RUNNER_TEMP:-/tmp}/sentry-cli"
if [[ ! -x "$CLI" ]]; then
  curl -sL "https://github.com/getsentry/sentry-cli/releases/download/2.39.1/sentry-cli-Linux-x86_64" -o "$CLI"
  chmod +x "$CLI"
fi

DSYM_DIR="$ARCHIVE_PATH/dSYMs"
if [[ ! -d "$DSYM_DIR" ]]; then
  echo "No dSYMs folder in archive; skipping."
  exit 0
fi

"$CLI" debug-files upload \
  --org "$ORG" \
  --project "$PROJECT" \
  --include-sources \
  "$DSYM_DIR"

"$CLI" releases new "$RELEASE" --org "$ORG" --project "$PROJECT" || true
"$CLI" releases finalize "$RELEASE" --org "$ORG" --project "$PROJECT" || true

echo "Uploaded dSYMs for release=$RELEASE"
