#!/usr/bin/env bash
# Runs the Android instrumented tests on the already booted emulator and collects evidence.
# Usage: device-tests.sh <api-level>
# Always exits 0 so evidence is collected; the Gradle result is written to device-<api>-result.txt.
set -u
API="$1"
EVIDENCE_DIR="androidApp/build/reports/runtime-evidence"
mkdir -p "$EVIDENCE_DIR"

adb logcat -c || true
./gradlew --stacktrace --continue :androidApp:connectedDebugAndroidTest >> "device-$API-log.txt" 2>&1
STATUS=$?
echo "$STATUS" > "device-$API-result.txt"
tail -n 40 "device-$API-log.txt"

{
  echo "emulator: $(adb shell getprop ro.build.version.release | tr -d '\r') / API $(adb shell getprop ro.build.version.sdk | tr -d '\r') / $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  adb logcat -d -v raw -s TodoEvidence:I | grep -v '^-----' || true
} > "$EVIDENCE_DIR/android-api$API.txt"
cat "$EVIDENCE_DIR/android-api$API.txt"
exit 0
