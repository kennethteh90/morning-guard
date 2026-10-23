#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PORT="${1:-8766}"
echo "Morning Guard iOS demo: http://localhost:${PORT}/"
echo "Press Ctrl+C to stop."
exec python3 -m http.server "$PORT" --directory "$ROOT/site/ios-demo"
