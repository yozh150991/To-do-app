#!/usr/bin/env bash
# CI: end-to-end test of the owner's release scripts with a throwaway key, exactly as the owner runs
# them: create-keystore.sh (password typed via stdin) → build-release.sh (test mode: no git/version
# checks, no tag, no install). Runs on Linux and on Windows in Git Bash.
# Usage: local-release-e2e.sh <label>   → evidence in androidApp/build/reports/runtime-evidence/
set -euo pipefail
LABEL="${1:-linux}"
WORK="$(mktemp -d)"
PASSWORD="ci-throwaway-$RANDOM$RANDOM$RANDOM" # not a secret: the key is discarded with the runner
EVIDENCE_DIR="androidApp/build/reports/runtime-evidence"
mkdir -p "$EVIDENCE_DIR"

echo "== create-keystore.sh (HOME=$WORK)"
printf '%s\n%s\n' "$PASSWORD" "$PASSWORD" | HOME="$WORK" bash tools/release/create-keystore.sh
KEY_DIR="$WORK/todo-release-key"
test -f "$KEY_DIR/todo-release.jks"
EXPECTED="$(tr -d '\r\n' < "$KEY_DIR/certificate-sha256.txt")"

echo "== build-release.sh (test mode)"
TODO_RELEASE_TEST=1 \
TODO_EXPECTED_CERT_SHA256="$EXPECTED" \
TODO_RELEASE_KEYSTORE="$KEY_DIR/todo-release.jks" \
TODO_RELEASE_KEYSTORE_PASSWORD="$PASSWORD" \
TODO_RELEASE_EVIDENCE="$EVIDENCE_DIR/release-apk-$LABEL.txt" \
  bash tools/release/build-release.sh 0.0.1

test -f dist/todo-v0.0.1.apk
test -f dist/todo-v0.0.1.apk.sha256
echo "== evidence"
cat "$EVIDENCE_DIR/release-apk-$LABEL.txt"
rm -rf "$WORK"
