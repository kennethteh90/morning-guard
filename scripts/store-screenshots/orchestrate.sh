#!/usr/bin/env bash
# End-to-end: plan (AI optional) -> device captures -> screenshot-studio compose.
# Copy / typography / preset changes only: ./compose.sh  (no device, no recapture)
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
USE_AI=false
COMPOSE_ONLY=false
for arg in "$@"; do
  case "$arg" in
    --ai) USE_AI=true ;;
    --compose-only) COMPOSE_ONLY=true ;;
  esac
done

if $COMPOSE_ONLY; then
  exec "$DIR/compose.sh"
fi

echo "=== 1/3 Store listing plan ==="
if $USE_AI; then
  node "$DIR/generate-plan.mjs" --ai --out "$DIR/campaign.json"
else
  node "$DIR/generate-plan.mjs" --out "$DIR/campaign.json"
fi

echo "=== 2/3 Device screenshots (screenshot build) ==="
CAMPAIGN="$DIR/campaign.json" RAW_DIR="$DIR/raw" "$DIR/capture-device.sh"

echo "=== 3/3 Screenshot studio compose ==="
if [[ ! -d "$DIR/node_modules/canvas" ]]; then
  echo "Installing compose dependencies (canvas)…"
  (cd "$DIR" && npm install --no-fund --no-audit)
fi
node "$DIR/compose.mjs" --campaign "$DIR/campaign.json" --raw "$DIR/raw" --out "$DIR/out"

echo ""
echo "Store-ready PNGs: $DIR/out/"
echo "Change copy, preset, or typography in $DIR/campaign.json — then compose only (no recapture):"
echo "  $DIR/compose.sh"
