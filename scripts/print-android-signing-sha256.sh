#!/usr/bin/env bash
# Print SHA-256 certificate fingerprints for Morning Guard signing keys.
#
# assetlinks.json needs the fingerprint of the certificate that signs the APK
# users install — NOT an RSA "public key" string.
#
# • Google Play installs (internal / production): use Play Console → App integrity
#   → App signing key certificate → SHA-256 certificate fingerprint.
# • Local release APK/AAB you sign with RELEASE_* only: use the upload keystore
#   fingerprint printed below (App Links will not verify for Play-installed builds).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="$ROOT/local.properties"
get_prop() { grep -E "^$1=" "$PROPS" 2>/dev/null | head -1 | cut -d= -f2- || true; }

format_assetlinks() {
  # assetlinks.json expects uppercase hex with colons (same as keytool SHA256 line).
  echo "$1" | tr '[:lower:]' '[:upper:]'
}

echo "=== Upload keystore (local.properties RELEASE_STORE_FILE) ==="
if [[ ! -f "$PROPS" ]]; then
  echo "Missing local.properties" >&2
  exit 1
fi
KS="$(get_prop RELEASE_STORE_FILE)"
ALIAS="$(get_prop RELEASE_KEY_ALIAS)"
SP="$(get_prop RELEASE_STORE_PASSWORD)"
if [[ -z "$KS" || -z "$ALIAS" || -z "$SP" ]]; then
  echo "Set RELEASE_STORE_FILE, RELEASE_KEY_ALIAS, RELEASE_STORE_PASSWORD in local.properties" >&2
  exit 1
fi
[[ "$KS" != /* ]] && KS="$ROOT/$KS"
if [[ ! -f "$KS" ]]; then
  echo "Keystore not found: $KS" >&2
  exit 1
fi
SHA="$(keytool -list -v -keystore "$KS" -alias "$ALIAS" -storepass "$SP" 2>/dev/null | awk -F': ' '/SHA256:/ {print $2; exit}')"
if [[ -z "$SHA" ]]; then
  echo "Could not read SHA256 from keystore (wrong alias/password?)" >&2
  exit 1
fi
SHA="$(format_assetlinks "$SHA")"
echo "SHA-256: $SHA"
echo
echo "Paste into assetlinks.json only if you sideload builds signed with this upload key."
echo "For Play Store / internal testing tracks, use Play Console instead:"
echo "  Morning Guard → Protected with Play → key management:"
echo "  https://play.google.com/console/u/3/developers/4637542060743917035/app/4974716491863892021/keymanagement"
echo "  → App signing key certificate → SHA-256 certificate fingerprint (not upload key)"
echo
echo "Update assetlinks.json:"
echo "  ./scripts/set-assetlinks-sha256.sh '$SHA'"
