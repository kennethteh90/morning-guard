#!/usr/bin/env bash
# Upload an Apple Distribution .p12 to GitHub Actions secrets for iOS Release CI.
#
# Export the cert from Keychain Access (recommended — macOS `security export` cannot
# filter to a single identity on recent macOS):
#   Keychain Access → My Certificates → expand "Apple Distribution: …" → select cert + private key
#   → File → Export Items… → Personal Information Exchange (.p12)
#
# Usage:
#   IOS_DISTRIBUTION_CERTIFICATE_P12_PATH=~/Downloads/Certificates.p12 \
#   IOS_DISTRIBUTION_CERTIFICATE_PASSWORD='your-p12-password' \
#   ./scripts/push-ios-distribution-cert-secret.sh
set -euo pipefail

REPO="kennethteh90/morning-guard"
P12_PATH="${IOS_DISTRIBUTION_CERTIFICATE_P12_PATH:-}"
PASSWORD="${IOS_DISTRIBUTION_CERTIFICATE_PASSWORD:-}"

if [[ -z "$P12_PATH" || ! -f "$P12_PATH" ]]; then
  echo "Set IOS_DISTRIBUTION_CERTIFICATE_P12_PATH to an Apple Distribution .p12 file." >&2
  echo "Export from Keychain Access (see script header)." >&2
  exit 1
fi
if [[ -z "$PASSWORD" ]]; then
  echo "Set IOS_DISTRIBUTION_CERTIFICATE_PASSWORD (encryption password for the .p12)." >&2
  exit 1
fi
if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required." >&2
  exit 1
fi

subject="$(openssl pkcs12 -in "$P12_PATH" -passin "pass:$PASSWORD" -legacy -nokeys -clcerts 2>/dev/null \
  | openssl x509 -noout -subject 2>/dev/null || true)"
if [[ "$subject" != *"Apple Distribution"* && "$subject" != *"iPhone Distribution"* ]]; then
  echo "This .p12 does not look like Apple Distribution." >&2
  echo "Subject: ${subject:-<could not read>}" >&2
  echo "Export only the Apple Distribution identity from Keychain Access, not Apple Development." >&2
  exit 1
fi
echo "Verified: $subject"

b64="$(base64 <"$P12_PATH" | tr -d '\n')"
printf '%s' "$b64" | gh secret set IOS_DISTRIBUTION_CERTIFICATE_P12 --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set IOS_DISTRIBUTION_CERTIFICATE_PASSWORD --repo "$REPO"
echo "Set IOS_DISTRIBUTION_CERTIFICATE_P12 and IOS_DISTRIBUTION_CERTIFICATE_PASSWORD on $REPO"
