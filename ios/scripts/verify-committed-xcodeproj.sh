#!/usr/bin/env bash
# Fail if MorningGuard.xcodeproj still differs from project.yml after XcodeGen.
# Intended for CI after ./scripts/generate-xcodeproj.sh (and locally before commit).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -d MorningGuard.xcodeproj ]]; then
  echo "MorningGuard.xcodeproj not found; run ./scripts/generate-xcodeproj.sh first." >&2
  exit 1
fi

if git diff --quiet -- MorningGuard.xcodeproj 2>/dev/null; then
  echo "MorningGuard.xcodeproj matches XcodeGen output."
  exit 0
fi

echo "Committed MorningGuard.xcodeproj is out of date relative to project.yml." >&2
echo "Run ./scripts/generate-xcodeproj.sh from ios/ and commit the result." >&2
git diff --stat -- MorningGuard.xcodeproj >&2 || true
exit 1
