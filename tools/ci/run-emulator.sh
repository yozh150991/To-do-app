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

log() { echo "$*" | tee -a "$LOG"; }

fail() {
  log "!! $*"
  if [ -f "$EMULATOR_LOG" ]; then
    log "== last emulator output =="
    tail -n 60 "$EMULATOR_LOG" | tee -a "$LOG"
  fi
  exit 1
}

log "== installing $PACKAGE, emulator, platform-tools"
yes | "$BIN/sdkmanager" --install "$PACKAGE" "emulator" "platform-tools" > sdk-install.txt 2>&1
INSTALL_STATUS=$?
grep -v '^\[' sdk-install.txt | tail -n 15 | tee -a "$LOG"
[ "$INSTALL_STATUS" -eq 0 ] || fail "sdkmanager failed with $INSTALL_STATUS"
log "emulator version: $("$SDK/emulator/emulator" -version 2>/dev/null | head -n 1)"

log "== creating AVD"
echo no | "$BIN/avdmanager" create avd --force -n ci -k "$PACKAGE" >> "$LOG" 2>&1 || fail "avdmanager failed"

log "== booting"
"$SDK/platform-tools/adb" start-server >> "$LOG" 2>&1
"$SDK/emulator/emulator" -avd ci -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim \
  -camera-back none -no-snapshot -accel on > "$EMULATOR_LOG" 2>&1 &
EMULATOR_PID=$!

DEADLINE=$((SECONDS + 900))
until [ "$("$SDK/platform-tools/adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  kill -0 "$EMULATOR_PID" 2>/dev/null || fail "emulator process exited during boot"
  [ "$SECONDS" -lt "$DEADLINE" ] || fail "boot did not complete in 15 minutes"
  sleep 5
done
log "booted after ${SECONDS}s"

ADB="$SDK/platform-tools/adb"
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$ADB" shell settings put global "$setting" 0 >/dev/null 2>&1 || true
done
"$ADB" shell input keyevent 82 >/dev/null 2>&1 || true

PATH="$SDK/platform-tools:$PATH" bash tools/ci/device-tests.sh "$API"

"$ADB" emu kill >/dev/null 2>&1 || true
wait "$EMULATOR_PID" 2>/dev/null || true
exit 0
