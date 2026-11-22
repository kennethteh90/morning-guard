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

# XcodeGen 2.47+ may add shouldAutocreateTestPlan to schemes; ignore that attribute-only drift.
if git diff -- MorningGuard.xcodeproj/xcshareddata/xcschemes 2>/dev/null \
  | grep -E '^[+-]' \
  | grep -v 'shouldAutocreateTestPlan' \
  | grep -q .; then
  :
else
  scheme_only=$(git diff --name-only -- MorningGuard.xcodeproj 2>/dev/null \
    | grep -c 'xcshareddata/xcschemes' || true)
  total_changed=$(git diff --name-only -- MorningGuard.xcodeproj 2>/dev/null | wc -l | tr -d ' ')
  if [ "$scheme_only" = "$total_changed" ] && [ "$scheme_only" -gt 0 ]; then
    echo "MorningGuard.xcodeproj matches XcodeGen (ignoring shouldAutocreateTestPlan scheme drift)."
    exit 0
  fi
fi

echo "Committed MorningGuard.xcodeproj is out of date relative to project.yml." >&2
echo "Run ./scripts/generate-xcodeproj.sh from ios/ and commit the result." >&2
git diff --stat -- MorningGuard.xcodeproj >&2 || true
exit 1
