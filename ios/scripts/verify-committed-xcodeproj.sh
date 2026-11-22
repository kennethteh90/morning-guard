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

./scripts/generate-xcodeproj.sh

if git diff --quiet -- MorningGuard.xcodeproj 2>/dev/null; then
  echo "MorningGuard.xcodeproj matches XcodeGen output."
  exit 0
fi

# XcodeGen 2.47 on CI may reorder TestAction attributes or add shouldAutocreateTestPlan.
if git diff --name-only -- MorningGuard.xcodeproj 2>/dev/null | grep -qv 'xcshareddata/xcschemes'; then
  echo "Committed MorningGuard.xcodeproj is out of date (project content)." >&2
  git diff --stat -- MorningGuard.xcodeproj >&2 || true
  exit 1
fi

noise=$(git diff -- MorningGuard.xcodeproj/xcshareddata/xcschemes 2>/dev/null \
  | grep -E '^[+-]' \
  | grep -v '^[+-]{3}' \
  | grep -v 'shouldAutocreateTestPlan' \
  | grep -v 'shouldUseLaunchSchemeArgsEnv' \
  | grep -v 'onlyGenerateCoverageForSpecifiedTargets' \
  | grep -v 'selectedDebuggerIdentifier' \
  | grep -v 'selectedLauncherIdentifier' \
  | grep -v 'buildConfiguration' || true)

if [ -z "$noise" ]; then
  echo "MorningGuard.xcodeproj matches XcodeGen (ignoring scheme TestAction attribute drift)."
  exit 0
fi

echo "Committed MorningGuard.xcodeproj is out of date relative to project.yml." >&2
echo "Run ./scripts/generate-xcodeproj.sh from ios/ and commit the result." >&2
git diff --stat -- MorningGuard.xcodeproj >&2 || true
exit 1
