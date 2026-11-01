#!/usr/bin/env bash
# Installs the previous version, lets it create its workspace, installs the current version over it
# (`adb install -r`, same signing key) and checks the data survived (spec §2.1.9, §3.4).
# Runs on a booted emulator; always exits 0 and writes the verdict to upgrade-result.txt.
set -u
PKG="io.github.yozh150991.todo.debug"
ACTIVITY="io.github.yozh150991.todo.android.MainActivity"
LOG="upgrade-log.txt"
EVIDENCE_DIR="androidApp/build/reports/runtime-evidence"
mkdir -p "$EVIDENCE_DIR" upgrade/before upgrade/after

log() { echo "$*" | tee -a "$LOG"; }

if [ -f upgrade/skip.txt ]; then
  cat upgrade/skip.txt | tee -a "$LOG" > "$EVIDENCE_DIR/upgrade.txt"
  echo 0 > upgrade-result.txt
  exit 0
fi

launch_and_wait() { # waits until the app has created and populated its database
  adb shell am start -W -n "$PKG/$ACTIVITY" >> "$LOG" 2>&1
  for _ in $(seq 1 60); do
    if adb shell run-as "$PKG" ls databases/todo.db >/dev/null 2>&1; then
      sleep 5
      break
    fi
    sleep 2
  done
  adb shell am force-stop "$PKG"
}

pull_db() { # copies the database files (including an un-checkpointed WAL) into $1
  local dir="$1"
  adb shell run-as "$PKG" ls -la databases > "$dir/listing.txt" 2>&1
  for f in todo.db todo.db-wal todo.db-shm; do
    adb exec-out run-as "$PKG" cat "databases/$f" > "$dir/$f" 2>/dev/null || true
    [ -s "$dir/$f" ] || rm -f "$dir/$f"
  done
}

log "== install previous ($(cat upgrade/previous-commit.txt))"
adb install upgrade/previous.apk >> "$LOG" 2>&1 || { log "install of previous failed"; echo 1 > upgrade-result.txt; exit 0; }
launch_and_wait
pull_db upgrade/before

log "== install current over it (adb install -r)"
adb install -r upgrade/current.apk >> "$LOG" 2>&1 || { log "upgrade install failed"; echo 1 > upgrade-result.txt; exit 0; }
launch_and_wait
pull_db upgrade/after
adb shell dumpsys package "$PKG" | grep -E "versionCode|versionName" | head -n 4 >> "$LOG"

python3 tools/ci/check-upgrade.py upgrade/before upgrade/after > "$EVIDENCE_DIR/upgrade.txt" 2>&1
STATUS=$?
cat "$EVIDENCE_DIR/upgrade.txt" | tee -a "$LOG"
echo "$STATUS" > upgrade-result.txt
exit 0
