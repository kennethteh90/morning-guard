#!/usr/bin/env bash
# Export Apple Distribution identity from the login keychain and push to GitHub Actions.
#
# Usage:
#   ./scripts/push-ios-distribution-cert-secret.sh
#   IOS_DISTRIBUTION_CERTIFICATE_PASSWORD='choose-a-strong-password' ./scripts/push-ios-distribution-cert-secret.sh
#
# Revoke duplicate Distribution certs in developer.apple.com if CI hit the two-cert limit.
set -euo pipefail

REPO="kennethteh90/morning-guard"
PASSWORD="${IOS_DISTRIBUTION_CERTIFICATE_PASSWORD:-}"

if [[ -z "$PASSWORD" ]]; then
  echo "Set IOS_DISTRIBUTION_CERTIFICATE_PASSWORD (used for the .p12 and GitHub secret)." >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required." >&2
  exit 1
fi

tmp="$(mktemp -t morning-guard-dist).p12"
trap 'rm -f "$tmp"' EXIT

security export -k "$HOME/Library/Keychains/login.keychain-db" -t identities -f pkcs12 \
  -o "$tmp" -P "$PASSWORD" 2>/dev/null || security export -t identities -f pkcs12 -o "$tmp" -P "$PASSWORD"

b64="$(base64 <"$tmp" | tr -d '\n')"
printf '%s' "$b64" | gh secret set IOS_DISTRIBUTION_CERTIFICATE_P12 --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set IOS_DISTRIBUTION_CERTIFICATE_PASSWORD --repo "$REPO"
echo "Set IOS_DISTRIBUTION_CERTIFICATE_P12 and IOS_DISTRIBUTION_CERTIFICATE_PASSWORD on $REPO"
