#!/usr/bin/env bash
# Record a Play Console notification-listener policy demo video on a connected device/emulator.
# Step-based choreography: holds, taps, warm scene changes (see notification-demo.json).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=demo-ui.sh
source "$DIR/demo-ui.sh"
SCRIPT="${SCRIPT:-$DIR/notification-demo.json}"
OUT_DIR="${OUT_DIR:-$DIR/out}"
PKG="com.morningguard.app.screenshot"
COMPONENT="$PKG/com.morningguard.app.MainActivity"
LISTENER="$PKG/com.morningguard.app.service.MorningNotificationListenerService"
REMOTE_VIDEO="/sdcard/morning-guard-notification-demo.mp4"

if [[ ! -f "$SCRIPT" ]]; then
  echo "Missing $SCRIPT"
  exit 1
fi

if ! command -v adb >/dev/null; then
  echo "adb not found"
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device connected. Start an emulator or plug in a phone, then re-run."
  exit 1
fi

mkdir -p "$OUT_DIR"

output_file="$(python3 - "$SCRIPT" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    print(json.load(f).get("outputFile", "notification-listener-demo.mp4"))
PY
)"
OUT_PATH="$OUT_DIR/$output_file"

SCENE_SETTLE="$(python3 - "$SCRIPT" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    d = json.load(f).get("defaults", {})
print(d.get("sceneSettleSeconds", 2.5))
PY
)"
AFTER_TAP="$(python3 - "$SCRIPT" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    d = json.load(f).get("defaults", {})
print(d.get("afterTapSeconds", 0.8))
PY
)"

total_seconds="$(python3 - "$SCRIPT" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
total = 5
for step in data["steps"]:
    total += float(step.get("seconds", 0))
    if step.get("do") == "launch_scene":
        total += float(data.get("defaults", {}).get("sceneSettleSeconds", 2.5))
    if step.get("do") == "tap_text":
        total += float(data.get("defaults", {}).get("afterTapSeconds", 0.8))
print(int(total) + 10)
PY
)"

wait_for_device_idle() {
  local i boot
  for i in $(seq 1 90); do
    boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if [[ "$boot" == "1" ]]; then
      break
    fi
    sleep 2
  done
  sleep 6
  # Keep animations on so navigation reads on video (unlike static screenshot capture).
  adb shell settings put global window_animation_scale 1 >/dev/null 2>&1 || true
  adb shell settings put global transition_animation_scale 1 >/dev/null 2>&1 || true
  adb shell settings put global animator_duration_scale 1 >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

wait_ready() {
  local want=$1
  local i got
  for i in $(seq 1 100); do
    got="$(adb shell run-as "$PKG" cat files/screenshot_scene_ready 2>/dev/null | tr -d '\r' || true)"
    if [[ "$got" == "$want" ]]; then
      return 0
    fi
    sleep 0.25
  done
  echo "Timed out waiting for scene=$want (last=$got)"
  return 1
}

launch_scene() {
  local scene=$1
  local cold=${2:-false}
  adb shell run-as "$PKG" rm -f files/screenshot_scene_ready 2>/dev/null || true
  if [[ "$cold" == "true" ]]; then
    echo "  (cold start: force-stop + splash — only at recording open)"
    adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
    sleep 0.4
    adb shell am start -S -n "$COMPONENT" -e screenshot_scene "$scene" >/dev/null
  else
    adb shell am start --activity-single-top -n "$COMPONENT" -e screenshot_scene "$scene" >/dev/null
  fi
  wait_ready "$scene"
  sleep "$SCENE_SETTLE"
}

open_listener_settings() {
  adb shell am start -a android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS \
    --es android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME "$LISTENER" >/dev/null 2>&1 \
    || adb shell am start -a android.settings.NOTIFICATION_LISTENER_SETTINGS >/dev/null
}

grant_listener() {
  adb shell cmd notification allow_listener "$LISTENER" >/dev/null 2>&1 \
    || adb shell settings put secure enabled_notification_listeners "$LISTENER" >/dev/null 2>&1 \
    || true
}

revoke_listener_for_settings_shot() {
  adb shell cmd notification disallow_listener "$LISTENER" >/dev/null 2>&1 || true
}

stop_screenrecord() {
  adb shell pkill -INT screenrecord >/dev/null 2>&1 || true
  sleep 2
}

step_fields() {
  local idx=$1
  python3 - "$SCRIPT" "$idx" <<'PY'
import json, sys
step = json.load(open(sys.argv[1]))["steps"][int(sys.argv[2])]
EMPTY = "∅"

def col(key, default=EMPTY):
    if key not in step and default is EMPTY:
        return EMPTY
    v = step.get(key, default)
    if isinstance(v, bool):
        return "true" if v else "false"
    if v is None or v == "":
        return EMPTY
    return str(v).replace("\t", " ")
print(
    "\t".join(
        [
            step["do"],
            col("scene"),
            col("cold"),
            col("seconds"),
            col("text"),
            col("times"),
            col("pkg"),
            col("fallback_x"),
            col("fallback_y"),
            step.get("note", "").replace("\t", " "),
        ]
    )
)
PY
}

echo "Building & installing screenshot variant…"
(cd "$ROOT" && ./gradlew :app:installScreenshot -q)

wait_for_device_idle
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
revoke_listener_for_settings_shot

adb shell rm -f "$REMOTE_VIDEO" >/dev/null 2>&1 || true
time_limit="$total_seconds"
echo "Recording (~${time_limit}s max) → $OUT_PATH"
adb shell screenrecord --time-limit "$time_limit" --bit-rate 8000000 "$REMOTE_VIDEO" &
RECORD_PID=$!
sleep 2

step_count="$(python3 - "$SCRIPT" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    print(len(json.load(f)["steps"]))
PY
)"

for idx in $(seq 0 $((step_count - 1))); do
  IFS=$'\t' read -r action scene cold seconds text times pkg fallback_x fallback_y note <<EOF
$(step_fields "$idx")
EOF
  [[ "$scene" == "∅" ]] && scene=""
  [[ "$cold" == "∅" ]] && cold="false"
  [[ "$seconds" == "∅" ]] && seconds=""
  [[ "$text" == "∅" ]] && text=""
  [[ "$times" == "∅" ]] && times="1"
  [[ "$pkg" == "∅" ]] && pkg=""
  [[ "$fallback_x" == "∅" ]] && fallback_x=""
  [[ "$fallback_y" == "∅" ]] && fallback_y=""
  [[ "$note" == "∅" ]] && note=""
  label="$action"
  [[ -n "$scene" ]] && label+=" $scene"
  [[ -n "$note" ]] && label+=" — $note"
  echo "Step $((idx + 1))/$step_count: $label"

  case "$action" in
    launch_scene)
      launch_scene "$scene" "${cold:-false}"
      ;;
    hold)
      sleep "$seconds"
      ;;
    tap_text)
      demo_tap_text "$text" "$fallback_x" "$fallback_y"
      sleep "$AFTER_TAP"
      ;;
    press_back)
      demo_press_back "${times:-1}"
      ;;
    wait_pkg)
      demo_wait_foreground_pkg "$pkg"
      ;;
    wait_settings)
      demo_wait_settings_screen
      ;;
    listener_settings)
      open_listener_settings
      ;;
    grant_listener)
      grant_listener
      ;;
    *)
      echo "Unknown step: $action"
      stop_screenrecord
      wait "$RECORD_PID" 2>/dev/null || true
      exit 1
      ;;
  esac
done

stop_screenrecord
wait "$RECORD_PID" 2>/dev/null || true

adb pull "$REMOTE_VIDEO" "$OUT_PATH"
adb shell rm -f "$REMOTE_VIDEO" >/dev/null 2>&1 || true

if command -v ffprobe >/dev/null 2>&1; then
  duration="$(ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$OUT_PATH" 2>/dev/null | cut -d. -f1)"
  echo "Done: $OUT_PATH (${duration}s)"
else
  echo "Done: $OUT_PATH"
fi

echo ""
echo "Upload in Play Console → App content → Sensitive app permissions → Notification listener."
