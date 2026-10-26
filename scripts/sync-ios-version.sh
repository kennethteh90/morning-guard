#!/usr/bin/env bash
# Sync MARKETING_VERSION and CURRENT_PROJECT_VERSION in ios/Config/MorningGuard-Shared.xcconfig
# from repo-root version.properties (same versionCode formula as Android).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="$ROOT/version.properties"
XCCONFIG="$ROOT/ios/Config/MorningGuard-Shared.xcconfig"

if [[ ! -f "$PROPS" ]]; then
  echo "Missing version.properties" >&2
  exit 1
fi
if [[ ! -f "$XCCONFIG" ]]; then
  echo "Missing $XCCONFIG" >&2
  exit 1
fi

major="$(grep -E '^versionMajor=' "$PROPS" | cut -d= -f2-)"
minor="$(grep -E '^versionMinor=' "$PROPS" | cut -d= -f2-)"
patch="$(grep -E '^versionPatch=' "$PROPS" | cut -d= -f2-)"
marketing="${major}.${minor}.${patch}"
build="$((major * 10000 + minor * 100 + patch))"
# TestFlight rejects a repeated CFBundleVersion. Each Actions run gets its own build.
if [[ "${CI:-}" == "true" && -n "${GITHUB_RUN_NUMBER:-}" ]]; then
  build="$((build * 100 + GITHUB_RUN_NUMBER))"
fi

tmp="$(mktemp)"
sed -E \
  -e "s/^MARKETING_VERSION = .*/MARKETING_VERSION = ${marketing}/" \
  -e "s/^CURRENT_PROJECT_VERSION = .*/CURRENT_PROJECT_VERSION = ${build}/" \
  "$XCCONFIG" >"$tmp"
mv "$tmp" "$XCCONFIG"

echo "iOS version → MARKETING_VERSION=${marketing} CURRENT_PROJECT_VERSION=${build}"
