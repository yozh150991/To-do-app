#!/usr/bin/env bash
# Builds the debug APK of the previous version (the PR base commit) and of the current checkout,
# signed with the same runner debug key, for the upgrade-over test (spec §3.4, §12.4 DoD).
# Usage: build-upgrade-pair.sh <previous-commit>
set -euo pipefail
PREVIOUS="$1"
OUT="upgrade"
mkdir -p "$OUT"

git fetch --no-tags origin "$PREVIOUS" || true
rm -rf ../previous-version
git worktree add --detach ../previous-version "$PREVIOUS"

if [ ! -f ../previous-version/androidApp/build.gradle.kts ]; then
  echo "Previous commit $PREVIOUS has no Android app yet; upgrade test skipped." | tee "$OUT/skip.txt"
  exit 0
fi

echo "== previous version: $PREVIOUS"
(cd ../previous-version && ./gradlew --stacktrace :androidApp:assembleDebug -PversionCode=1 -PversionName=previous)
cp ../previous-version/androidApp/build/outputs/apk/debug/androidApp-debug.apk "$OUT/previous.apk"

echo "== current version: $(git rev-parse HEAD)"
./gradlew --stacktrace :androidApp:assembleDebug -PversionCode=2 -PversionName=current
cp androidApp/build/outputs/apk/debug/androidApp-debug.apk "$OUT/current.apk"

echo "$PREVIOUS" > "$OUT/previous-commit.txt"
ls -la "$OUT"
