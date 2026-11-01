#!/usr/bin/env bash
# Boots an Android emulator, runs the instrumented tests and shuts the emulator down.
# Every step is logged into device-<api>-log.txt so the PR report shows why a run failed.
#
# Usage: run-emulator.sh <api> <system-image-api> <target>
#   e.g. run-emulator.sh 26 26 default
#        run-emulator.sh 37 37.0 google_apis   (Android 17+ images carry a minor level)
set -u
API="$1"
IMAGE_API="$2"
TARGET="$3"
LOG="device-$API-log.txt"
EMULATOR_LOG="device-$API-emulator.txt"
SDK="${ANDROID_HOME:?ANDROID_HOME is not set}"
BIN="$SDK/cmdline-tools/latest/bin"
PACKAGE="system-images;android-$IMAGE_API;$TARGET;x86_64"
# avdmanager and the emulator must agree on where AVDs live.
export ANDROID_AVD_HOME="$HOME/.android/avd"
mkdir -p "$ANDROID_AVD_HOME"

log() { echo "$*" | tee -a "$LOG"; }

fail() {
  if [ -f "$EMULATOR_LOG" ]; then
    log "== last emulator output (INFO lines omitted) =="
    grep -v '^INFO' "$EMULATOR_LOG" | tail -n 40 | tee -a "$LOG"
  fi
  log "!! $*"
  exit 1
}

log "== installing $PACKAGE, emulator, platform-tools"
yes | "$BIN/sdkmanager" --install "$PACKAGE" "emulator" "platform-tools" > sdk-install.txt 2>&1
INSTALL_STATUS=$?
grep -v '%' sdk-install.txt | tail -n 15 | tee -a "$LOG"
[ "$INSTALL_STATUS" -eq 0 ] || fail "sdkmanager failed with $INSTALL_STATUS"

log "== creating AVD"
echo no | "$BIN/avdmanager" create avd --force -n ci -k "$PACKAGE" -p "$ANDROID_AVD_HOME/ci.avd" 2>&1 \
  | grep -v '%' | tee -a "$LOG"
[ "${PIPESTATUS[1]}" -eq 0 ] || fail "avdmanager failed"
log "AVDs known to the emulator: $("$SDK/emulator/emulator" -list-avds 2>&1 | tr '\n' ' ')"
ls -la "$ANDROID_AVD_HOME" >> "$LOG" 2>&1

log "== booting"
"$SDK/platform-tools/adb" start-server >> "$LOG" 2>&1
"$SDK/emulator/emulator" -avd ci -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim \
  -camera-back none -no-snapshot -accel on -memory 3072 -cores 3 > "$EMULATOR_LOG" 2>&1 &
EMULATOR_PID=$!

BOOT_START=$SECONDS
DEADLINE=$((SECONDS + 900))
NEXT_REPORT=$((SECONDS + 60))
until [ "$("$SDK/platform-tools/adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  kill -0 "$EMULATOR_PID" 2>/dev/null || fail "emulator process exited during boot"
  [ "$SECONDS" -lt "$DEADLINE" ] || fail "boot did not complete in 15 minutes"
  if [ "$SECONDS" -ge "$NEXT_REPORT" ]; then
    log "  ...$((SECONDS - BOOT_START))s: adb state=$("$SDK/platform-tools/adb" get-state 2>&1 | tr -d '\r'), bootanim=$("$SDK/platform-tools/adb" shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')"
    NEXT_REPORT=$((SECONDS + 60))
  fi
  sleep 5
done
log "booted after $((SECONDS - BOOT_START))s"

ADB="$SDK/platform-tools/adb"
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$ADB" shell settings put global "$setting" 0 >/dev/null 2>&1 || true
done
"$ADB" shell input keyevent 82 >/dev/null 2>&1 || true

PATH="$SDK/platform-tools:$PATH" bash tools/ci/device-tests.sh "$API"

"$ADB" emu kill >/dev/null 2>&1 || true
wait "$EMULATOR_PID" 2>/dev/null || true
exit 0
