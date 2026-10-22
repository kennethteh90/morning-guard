#!/usr/bin/env bash
# Shared adb UI helpers for store demo recording (tap by visible text).
set -euo pipefail

demo_tap_text() {
  local needle=$1
  local fallback_x=${2:-}
  local fallback_y=${3:-}
  adb shell uiautomator dump /sdcard/uidump.xml >/dev/null 2>&1 || true
  local tapped
  tapped="$(adb shell cat /sdcard/uidump.xml 2>/dev/null | python3 - "$needle" <<'PY'
import re, sys
needle = sys.argv[1].lower()
xml = sys.stdin.read()
candidates = []
patterns = [
    r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
    r'content-desc="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
    r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="([^"]*)"',
    r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*content-desc="([^"]*)"',
]
for pat in patterns:
    for m in re.finditer(pat, xml):
        g = m.groups()
        if len(g) == 5 and not g[0].isdigit():
            label, x1, y1, x2, y2 = g[0], g[1], g[2], g[3], g[4]
        else:
            x1, y1, x2, y2, label = g[0], g[1], g[2], g[3], g[4]
        if needle in (label or "").lower():
            x1, y1, x2, y2 = map(int, (x1, y1, x2, y2))
            candidates.append((x1 + x2) // 2, (y1 + y2) // 2)
if not candidates:
    sys.exit(1)
print(f"{candidates[0][0]} {candidates[0][1]}")
PY
)" || true
  if [[ -z "$tapped" ]]; then
    if [[ -n "$fallback_x" && -n "$fallback_y" ]]; then
      echo "Text not found ($needle); using fallback tap ${fallback_x},${fallback_y}"
      adb shell input tap "$fallback_x" "$fallback_y" >/dev/null
      return 0
    fi
    echo "Could not find UI text matching: $needle"
    return 1
  fi
  adb shell input tap "$tapped" >/dev/null
}

demo_press_back() {
  local times=${1:-1}
  local i
  for i in $(seq 1 "$times"); do
    adb shell input keyevent 4 >/dev/null
    sleep 0.35
  done
}

demo_wait_settings_screen() {
  local i focus
  for i in $(seq 1 50); do
    focus="$(adb shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' || true)"
    if echo "$focus" | grep -qE 'com\.android\.settings|permissioncontroller|notification'; then
      return 0
    fi
    sleep 0.25
  done
  echo "Timed out waiting for notification access settings (focus: $focus)"
  return 1
}

demo_wait_foreground_pkg() {
  local pkg=$1
  local i focus
  for i in $(seq 1 40); do
    focus="$(adb shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' || true)"
    if echo "$focus" | grep -q "$pkg"; then
      return 0
    fi
    sleep 0.25
  done
  echo "Timed out waiting for foreground $pkg (focus: $focus)"
  return 1
}
