#!/usr/bin/env bash
# Runs only SupabaseDeviceTest on the booted emulator against the local Supabase stack on the host.
# The emulator's 10.0.2.2 alias timed out against the Docker-published port on the CI runner, so the
# port is forwarded with `adb reverse` and the app connects to 127.0.0.1 on the device.
# Reads the anon key from supabase.env (supabase-start.sh).
# Always exits 0; the verdict goes to supabase-android-result.txt.
set -u
LOG="supabase-android-log.txt"
EVIDENCE_DIR="androidApp/build/reports/runtime-evidence"
mkdir -p "$EVIDENCE_DIR"
# shellcheck disable=SC1091
. ./supabase.env
PORT="${SUPABASE_URL##*:}"
adb reverse "tcp:$PORT" "tcp:$PORT" >> "$LOG" 2>&1
{
  echo "== adb reverse"; adb reverse --list
  echo "== Docker port bindings"; docker ps --format '{{.Names}} {{.Ports}}' | grep -i kong || true
} >> "$LOG" 2>&1
EMULATOR_URL="http://127.0.0.1:$PORT"

adb logcat -c || true
# The probe has its own per-check timeouts; this bound keeps a stuck instrumentation from eating the job.
timeout 900 ./gradlew --stacktrace :androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.yozh150991.todo.android.SupabaseDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.supabaseUrl="$EMULATOR_URL" \
  -Pandroid.testInstrumentationRunnerArguments.supabaseKey="$SUPABASE_ANON_KEY" >> "$LOG" 2>&1
STATUS=$?
echo "$STATUS" > supabase-android-result.txt
tail -n 30 "$LOG"
{
  echo "emulator: $(adb shell getprop ro.build.version.release | tr -d '\r') / API $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  adb logcat -d -v raw -s TodoEvidence:I | grep -v '^-----' || true
  echo "== app and test runner errors (logcat E, last 60 lines)"
  adb logcat -d -v brief '*:E' | grep -v '^-----' | tail -n 60 || true
} > "$EVIDENCE_DIR/supabase-android.txt"
cat "$EVIDENCE_DIR/supabase-android.txt"
exit 0
