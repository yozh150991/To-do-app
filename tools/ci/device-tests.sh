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

# Maestro flows (androidApp/maestro) against the debug build; connectedAndroidTest uninstalled it.
if command -v maestro > /dev/null; then
  OUT="maestro-$API"
  mkdir -p "$OUT"
  APK=$(ls androidApp/build/outputs/apk/debug/*.apk 2>/dev/null | head -n 1)
  MAESTRO_STATUS=1
  if [ -n "$APK" ] && adb install -r "$APK" > "$OUT/install.txt" 2>&1; then
    (cd "$OUT" && maestro test --format junit --output report.xml ../androidApp/maestro) > "$OUT/log.txt" 2>&1
    MAESTRO_STATUS=$?
  else
    echo "could not install $APK" >> "$OUT/install.txt"
  fi
  {
    echo "maestro $(maestro --version 2>/dev/null | tail -n 1), API $API: $([ "$MAESTRO_STATUS" = 0 ] && echo pass || echo fail)"
    sed 's/\x1b\[[0-9;]*m//g' "$OUT/log.txt" 2>/dev/null | grep -v '^[[:space:]]*$' | tail -n 25
    cat "$OUT/install.txt"
  } > "$EVIDENCE_DIR/maestro-api$API.txt"
  cat "$EVIDENCE_DIR/maestro-api$API.txt"
  # The job fails if either the instrumented tests or the Maestro flows failed.
  [ "$MAESTRO_STATUS" = 0 ] || [ "$STATUS" != 0 ] || echo "$MAESTRO_STATUS" > "device-$API-result.txt"
fi
exit 0
