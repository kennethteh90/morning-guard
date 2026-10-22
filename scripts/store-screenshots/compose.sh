#!/usr/bin/env bash
# Re-frame existing raw/*.png — edit campaign.json (copy, preset, typography) then run this.
# Does NOT touch the device or re-capture screens.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ ! -d "$DIR/node_modules/canvas" ]]; then
  echo "Installing compose dependencies (canvas)…"
  (cd "$DIR" && npm install --no-fund --no-audit)
fi

node "$DIR/compose.mjs" --campaign "$DIR/campaign.json" --raw "$DIR/raw" --out "$DIR/out"
echo "Done — $DIR/out/"
