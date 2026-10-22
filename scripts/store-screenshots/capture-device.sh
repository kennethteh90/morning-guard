#!/usr/bin/env bash
# Step 2 — install screenshot build, seed demo data, capture each scene via adb.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="$(cd "$(dirname "$0")" && pwd)"
CAMPAIGN="${CAMPAIGN:-$DIR/campaign.json}"
RAW_DIR="${RAW_DIR:-$DIR/raw}"
PKG="com.morningguard.app.screenshot"
COMPONENT="$PKG/com.morningguard.app.MainActivity"

if [[ ! -f "$CAMPAIGN" ]]; then
  echo "Missing $CAMPAIGN — run: node $DIR/generate-plan.mjs"
  exit 1
fi

if ! command -v adb >/dev/null; then
  echo "adb not found; connect an emulator or device."
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device connected."
  exit 1
fi

current_focus() {
  adb shell dumpsys window 2>/dev/null | grep -m1 "mCurrentFocus=" || true
}

dismiss_system_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
}

anr_dialog_on_screen() {
  system_anr_visible && return 0
  adb shell uiautomator dump /sdcard/uidump.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/uidump.xml 2>/dev/null | grep -qiE "isn.t responding|Not Responding"
}

# Tap "Wait" on System UI ANR if present (emulator often needs this before screencap).
dismiss_anr_if_present() {
  if ! anr_dialog_on_screen; then
    return 0
  fi
  adb shell uiautomator dump /sdcard/uidump.xml >/dev/null 2>&1 || true
  local tapped
  tapped="$(adb shell cat /sdcard/uidump.xml 2>/dev/null | python3 - <<'PY'
import re, sys
xml = sys.stdin.read()
for label in ("Wait", "wait"):
    m = re.search(
        rf'text="{label}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    )
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
        break
PY
)" || true
  if [[ -n "$tapped" ]]; then
    adb shell input tap "$tapped" >/dev/null 2>&1 || true
  else
    adb shell input tap 540 1600 >/dev/null 2>&1 || true
  fi
  sleep 2
  dismiss_system_dialogs
}

system_anr_visible() {
  adb shell dumpsys window 2>/dev/null | grep -qiE "Application Not Responding|Not Responding"
}

assert_no_system_anr() {
  if system_anr_visible; then
    echo "Device shows a system ANR dialog ($(current_focus))."
    echo "Restart the emulator (or tap Wait / reboot) and re-run capture."
    exit 1
  fi
}

wait_for_device_idle() {
  local i boot
  for i in $(seq 1 90); do
    boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if [[ "$boot" == "1" ]] && ! system_anr_visible; then
      break
    fi
    sleep 2
  done
  assert_no_system_anr
  # Let System UI settle after cold boot (avoids "System UI isn't responding" in screencaps).
  sleep 45
  dismiss_system_dialogs
  adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
  adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
  adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
  adb shell input keyevent 82 >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  assert_no_system_anr
}

mkdir -p "$RAW_DIR"
wait_for_device_idle

echo "Building & installing screenshot variant…"
(cd "$ROOT" && ./gradlew :app:installScreenshot)

adb shell am force-stop "$PKG" || true
adb shell pm clear "$PKG" || true
sleep 2
dismiss_system_dialogs
# Avoid permission dialog blocking later launches (adb consumes stdin inside capture loops).
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

slide_lines=()
while IFS= read -r line; do
  slide_lines+=("$line")
done < <(python3 - "$CAMPAIGN" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
for slide in data["slides"]:
    print(f"{slide['scene']}|{slide['rawFile']}")
PY
)

wait_ready() {
  local want=$1
  local i
  for i in $(seq 1 60); do
    local got
    got="$(adb shell run-as "$PKG" cat files/screenshot_scene_ready 2>/dev/null | tr -d '\r' || true)"
    if [[ "$got" == "$want" ]]; then
      return 0
    fi
    sleep 0.25
  done
  echo "Timed out waiting for scene=$want (last ready=$got)"
  return 1
}

scene_settle_seconds() {
  case "$1" in
    digest) echo 5 ;;
    goal) echo 5 ;;
    *) echo 1.5 ;;
  esac
}

capture_scene() {
  local scene=$1
  local outfile=$2
  assert_no_system_anr
  adb shell run-as "$PKG" rm -f files/screenshot_scene_ready 2>/dev/null || true
  adb shell am start -S -n "$COMPONENT" -e screenshot_scene "$scene" >/dev/null
  wait_ready "$scene"
  sleep "$(scene_settle_seconds "$scene")"
  assert_no_system_anr
  local focus
  focus="$(current_focus)"
  if ! echo "$focus" | grep -q "$PKG"; then
    echo "Expected $PKG in foreground before screencap; got: $focus"
    exit 1
  fi
  local attempt
  for attempt in 1 2 3; do
    dismiss_anr_if_present
    dismiss_system_dialogs
    sleep 0.4
    assert_no_system_anr
    adb exec-out screencap -p > "$outfile"
    if ! anr_dialog_on_screen; then
      break
    fi
    echo "ANR still visible after screencap (attempt $attempt); retrying…"
    dismiss_anr_if_present
    sleep 2
  done
  assert_no_system_anr
  echo "Captured $scene -> $outfile"
}

for line in "${slide_lines[@]}"; do
  scene="${line%%|*}"
  file="${line##*|}"
  capture_scene "$scene" "$RAW_DIR/$file"
  sleep 3
done

echo "Raw captures in $RAW_DIR"
