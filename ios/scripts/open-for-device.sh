#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! xcode-select -p 2>/dev/null | grep -q "Xcode.app"; then
  echo "Full Xcode is required. Install from the App Store, then run:" >&2
  echo "  sudo xcode-select -s /Applications/Xcode.app/Contents/Developer" >&2
  exit 1
fi

if [[ ! -f Config/Local.xcconfig ]]; then
  echo "Tip: copy Config/Local.xcconfig.example → Config/Local.xcconfig and set DEVELOPMENT_TEAM." >&2
fi

"$(dirname "$0")/generate-xcodeproj.sh"
open "$ROOT/MorningGuard.xcodeproj"
echo "Select your iPhone as the run destination and press ⌘R."
