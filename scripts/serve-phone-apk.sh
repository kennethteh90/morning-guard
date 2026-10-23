#!/usr/bin/env bash
# Build an APK and serve it on your LAN — open the URL on your phone to download & install.
# No USB debugging; you only need "Install unknown apps" allowed for your browser once.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VARIANT="debug"
PORT="${PORT:-8765}"

usage() {
  cat <<'EOF'
Usage: scripts/serve-phone-apk.sh [--release] [--demo]

  --release  serve signed release APK (RELEASE_* in local.properties)
  --demo     serve Morning Guard Demo APK (demo flavor, scenario picker)

Phone and Mac must be on the same Wi‑Fi. Open the printed http:// URL in Chrome,
download morning-guard.apk, tap to install (upgrades in place if the signing key matches).

Ctrl+C stops the server.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --demo) VARIANT="demo" ;;
    --release) VARIANT="release" ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

if [[ "$VARIANT" == "release" ]]; then
  ./gradlew :app:assembleFullRelease
  SRC="app/build/outputs/apk/full/release/app-full-release.apk"
elif [[ "$VARIANT" == "demo" ]]; then
  ./gradlew :app:assembleDemoDebug
  SRC="app/build/outputs/apk/demo/debug/app-demo-debug.apk"
else
  ./gradlew :app:assembleFullDebug
  SRC="app/build/outputs/apk/full/debug/app-full-debug.apk"
fi

SERVE_DIR="$(mktemp -d)"
trap 'rm -rf "$SERVE_DIR"' EXIT
cp "$SRC" "$SERVE_DIR/morning-guard.apk"

local_ip() {
  for iface in en0 en1 wlan0; do
    ip="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
    [[ -n "${ip:-}" ]] && echo "$ip" && return 0
  done
  hostname -I 2>/dev/null | awk '{print $1}'
}

IP="$(local_ip || true)"
if [[ -z "${IP:-}" ]]; then
  echo "Could not detect LAN IP. Set PORT and use your Mac's Wi‑Fi address manually." >&2
  IP="127.0.0.1"
fi

URL="http://${IP}:${PORT}/morning-guard.apk"
echo ""
echo "  $URL"
echo ""
echo "Serving $(basename "$SRC") — rebuild and re-run this script after code changes."
exec python3 - "$PORT" "$SERVE_DIR" <<'PY'
import sys
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer


class Handler(SimpleHTTPRequestHandler):
    # Phones often reset the socket after download; stdlib logs scary tracebacks for that.
    def handle(self):
        try:
            super().handle()
        except (BrokenPipeError, ConnectionResetError):
            pass

    def log_message(self, fmt, *args):
        # Skip probe noise; keep real requests.
        if args and args[0].startswith(("GET /morning-guard.apk", "HEAD /morning-guard.apk")):
            print(f"{self.address_string()} — {fmt % args}")


port = int(sys.argv[1])
directory = sys.argv[2]
with ThreadingHTTPServer(
    ("0.0.0.0", port),
    partial(Handler, directory=directory),
) as httpd:
    httpd.serve_forever()
PY
