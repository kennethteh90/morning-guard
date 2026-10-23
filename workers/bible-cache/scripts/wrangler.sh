#!/usr/bin/env bash
# Run wrangler from repo root or this worker dir (always uses workers/bible-cache/wrangler.jsonc).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec npx wrangler --config "$ROOT/wrangler.jsonc" "$@"
