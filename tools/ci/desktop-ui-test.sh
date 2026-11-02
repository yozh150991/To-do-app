#!/usr/bin/env bash
# Desktop runtime UI check (Ф0, spec §3.3.1 «Desktop/JVM»): the packaged app (jpackage image from
# `createDistributable`, as it will ship in Ф8) is started on a virtual X display, a task is typed
# into the real Compose window, the process is killed hard, the database is read with the sqlite3
# CLI, and the app is started again on the same data.
#
# Run under Xvfb:  xvfb-run -a --server-args="-screen 0 1280x800x24" bash tools/ci/desktop-ui-test.sh
# Needs: xdotool, imagemagick (import), sqlite3, x11-utils; openbox if present (window focus needs a WM).
# Always exits 0; the verdict goes to desktop-ui-result.txt.
set -u

LOG="desktop-ui-log.txt"
EVIDENCE_DIR="desktopApp/build/reports/runtime-evidence"
SHOTS="desktopApp/build/reports/desktop-ui"
EVIDENCE="$EVIDENCE_DIR/desktop-ui.txt"
TASK_TEXT="Typed in the desktop UI"
WINDOW_TITLE="To-do"
mkdir -p "$EVIDENCE_DIR" "$SHOTS"
: > "$EVIDENCE"
echo 1 > desktop-ui-result.txt

log() { echo "$*" | tee -a "$LOG"; }
evidence() { echo "$*" | tee -a "$LOG" >> "$EVIDENCE"; }
fail() {
  evidence "FAIL: $*"
  stop_app
  exit 0
}

APP_PID=""
stop_app() {
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill -KILL "$APP_PID" 2>/dev/null
    wait "$APP_PID" 2>/dev/null
  fi
  APP_PID=""
}

sql() { sqlite3 -batch -noheader "$DATA_DIR/todo.db" "$1" 2>&1; }

# --- build -----------------------------------------------------------------------------------
log "== ./gradlew :desktopApp:createDistributable"
if ! ./gradlew --stacktrace :desktopApp:createDistributable >> "$LOG" 2>&1; then
  fail "createDistributable failed (see desktop-ui-log.txt)"
fi
LAUNCHER=$(find desktopApp/build/compose/binaries/main/app -path '*/bin/*' -type f -perm -u+x 2>/dev/null | head -n 1)
[ -n "$LAUNCHER" ] || fail "no launcher in desktopApp/build/compose/binaries/main/app"
IMAGE_SIZE=$(du -sh "$(dirname "$(dirname "$LAUNCHER")")" | cut -f1)
evidence "launcher: $LAUNCHER (app image $IMAGE_SIZE, bundled runtime)"
evidence "display: ${DISPLAY:-none}, $(xdpyinfo 2>/dev/null | grep -m1 dimensions | xargs)"

# AWT only treats a window as focused (and delivers keys to Compose) when a window manager activates it.
if command -v openbox >/dev/null 2>&1; then
  openbox --sm-disable >> "$LOG" 2>&1 &
  sleep 2
  evidence "window manager: openbox"
fi

DATA_DIR=$(mktemp -d)
evidence "data dir: $DATA_DIR"

# Software rendering: Xvfb has no GPU. todo.dataDir keeps the run away from ~/.todo-app.
export JAVA_TOOL_OPTIONS="-Dtodo.dataDir=$DATA_DIR -Dskiko.renderApi=SOFTWARE"

start_app() { # $1 = label; sets APP_PID and WID
  local label="$1"
  "$LAUNCHER" > "$SHOTS/app-$label.log" 2>&1 &
  APP_PID=$!
  WID=""
  for _ in $(seq 1 90); do
    WID=$(xdotool search --onlyvisible --name "^${WINDOW_TITLE}\$" 2>/dev/null | head -n 1)
    [ -n "$WID" ] && break
    kill -0 "$APP_PID" 2>/dev/null || break
    sleep 1
  done
  if [ -z "$WID" ]; then
    evidence "app output ($label):"
    sed 's/^/  /' "$SHOTS/app-$label.log" | tail -n 40 >> "$EVIDENCE"
    fail "$label: no visible window titled '$WINDOW_TITLE' within 90 s"
  fi
  sleep 4 # first composition and database open
  eval "$(xdotool getwindowgeometry --shell "$WID")"
  evidence "$label: window $WID at ${X},${Y} size ${WIDTH}x${HEIGHT}"
  import -window root "$SHOTS/$label.png" 2>> "$LOG" || true
}

# --- first start: type a task into the real window --------------------------------------------
start_app first-start
# InboxScreen: the new-task field sits under the 64dp top bar, 16dp from the left; density 1 on Xvfb.
xdotool windowactivate --sync "$WID" >> "$LOG" 2>&1 || xdotool windowfocus --sync "$WID" >> "$LOG" 2>&1 || true
xdotool mousemove --window "$WID" 200 92 click 1 >> "$LOG" 2>&1
sleep 1
xdotool type --delay 40 "$TASK_TEXT" >> "$LOG" 2>&1
xdotool key Return >> "$LOG" 2>&1
sleep 3
import -window root "$SHOTS/after-typing.png" 2>> "$LOG" || true

# Hard kill, as a crash or power loss would: committed transactions must survive (SQLite WAL).
stop_app
ls -la "$DATA_DIR" | sed 's/^/  /' >> "$EVIDENCE"

[ -f "$DATA_DIR/todo.db" ] || fail "no todo.db in the data dir"
evidence "sqlite3 CLI: $(sqlite3 --version | cut -d' ' -f1)"
evidence "integrity_check: $(sql 'PRAGMA integrity_check')"
evidence "user_version: $(sql 'PRAGMA user_version')"
evidence "journal_mode: $(sql 'PRAGMA journal_mode')"
evidence "workspaces: $(sql 'SELECT COUNT(*) FROM local_workspace')"
evidence "open tasks: $(sql "SELECT group_concat(content, ' | ') FROM task WHERE completed_at IS NULL AND deleted_at IS NULL")"
evidence "outbox: $(sql "SELECT group_concat(local_seq || ':' || op_type, ', ') FROM outbox_operation")"

TASKS=$(sql "SELECT COUNT(*) FROM task WHERE content = '$TASK_TEXT' AND deleted_at IS NULL")
[ "$TASKS" = "1" ] || fail "the typed task is not in todo.db (found $TASKS)"
WORKSPACE=$(sql 'SELECT workspace_id FROM local_workspace')

# --- second start on the same data -----------------------------------------------------------
start_app restart
stop_app
[ "$(sql 'SELECT workspace_id FROM local_workspace')" = "$WORKSPACE" ] || fail "workspace changed after restart"
[ "$(sql "SELECT COUNT(*) FROM task WHERE content = '$TASK_TEXT'")" = "1" ] || fail "task lost after restart"
[ "$(sql 'SELECT COUNT(*) FROM project WHERE is_inbox = 1')" = "1" ] || fail "not exactly one Inbox after restart"
evidence "restart: same workspace, the task and one Inbox are still there"

for label in first-start restart; do
  evidence "app output ($label):"
  sed 's/^/  /' "$SHOTS/app-$label.log" | tail -n 15 >> "$EVIDENCE"
done

evidence "PASS"
echo 0 > desktop-ui-result.txt
