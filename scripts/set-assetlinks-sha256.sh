#!/usr/bin/env bash
# Set Play App Signing (or upload) SHA-256 in site/auth-landing/.well-known/assetlinks.json
# Usage: ./scripts/set-assetlinks-sha256.sh 'AA:BB:CC:...'
set -euo pipefail
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 'SHA256:FINGERPRINT:WITH:COLONS'" >&2
  exit 1
fi
FP="$1"
FP="$(echo "$FP" | tr '[:lower:]' '[:upper:]' | tr -d '[:space:]')"
if [[ ! "$FP" =~ ^([0-9A-F]{2}:){31}[0-9A-F]{2}$ ]]; then
  echo "Expected 32-byte SHA-256 fingerprint with colons (64 hex chars)" >&2
  exit 1
fi
FILE="$(cd "$(dirname "$0")/.." && pwd)/site/auth-landing/.well-known/assetlinks.json"
python3 - "$FP" "$FILE" <<'PY'
import json, sys
fp, path = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    data = json.load(f)
data[0]["target"]["sha256_cert_fingerprints"] = [fp]
with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=2)
    f.write("\n")
print(f"Updated {path}")
PY
echo "Redeploy: ./site/auth-landing/deploy.sh"
