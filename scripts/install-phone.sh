#!/usr/bin/env bash
# Build and install Morning Guard on a connected phone (USB or wireless ADB).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VARIANT="debug"
LAUNCH=1

usage() {
  cat <<'EOF'
Usage: scripts/install-phone.sh [--release] [--no-launch]

  --release    install signed release (needs RELEASE_* in local.properties)
  --no-launch  skip opening the app after install

One-time phone setup:
  1. Settings → About → tap Build number 7× → Developer options
  2. Enable USB debugging (USB) or Wireless debugging (Wi‑Fi)
  3. Plug in USB, accept the RSA prompt — or pair wireless:
       adb pair <ip>:<pairing-port>   # enter the 6-digit code on the phone
       adb connect <ip>:<connect-port>

Then: scripts/install-phone.sh
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release) VARIANT="release" ;;
    --no-launch) LAUNCH=0 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

if ! command -v adb >/dev/null; then
  echo "adb not found. Install Android platform-tools or open Android Studio SDK." >&2
  exit 1
fi

mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ ${#devices[@]} -eq 0 ]]; then
  echo "No adb device in 'device' state." >&2
  echo "Connect USB or run: adb connect <ip>:<port>" >&2
  adb devices >&2 || true
  exit 1
fi
if [[ ${#devices[@]} -gt 1 ]]; then
  echo "Multiple devices; using first: ${devices[0]}" >&2
fi
export ANDROID_SERIAL="${devices[0]}"
echo "Using device: $ANDROID_SERIAL"

if [[ "$VARIANT" == "release" ]]; then
  ./gradlew :app:installRelease
else
  ./gradlew :app:installDebug
fi

if [[ "$LAUNCH" -eq 1 ]]; then
  adb shell am start -n com.morningguard.app/.MainActivity
fi

echo "Done."
