#!/usr/bin/env bash
# PreToolUse hook for Bash: enforces the PR-only workflow (CLAUDE.md, rules 1 and 9).
# Reads the hook JSON from stdin. Exit 2 blocks the command and shows stderr to Claude.
# Plain bash + grep/sed only, so it also works in Git Bash on Windows.

input="$(cat)"

deny() {
  echo "Заблоковано harness: $1" >&2
  exit 2
}

has() { printf '%s' "$input" | grep -Eq -- "$1"; }

# Branch of the directory the command runs in (falls back to the hook's own cwd).
cwd="$(printf '%s' "$input" | sed -n 's/.*"cwd"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
branch="$(git -C "${cwd:-.}" rev-parse --abbrev-ref HEAD 2>/dev/null || git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"

if has 'git[[:space:]]+(-[^[:space:]]+[[:space:]]+)*commit'; then
  case "$branch" in
    main|master) deny "коміт у '$branch'. Створи гілку <тип>/<slug> і працюй у ній." ;;
  esac
fi

if has 'git[[:space:]]+(-[^[:space:]]+[[:space:]]+)*push'; then
  has '(--force|[[:space:]]-f([[:space:]]|\\|"|$)|--delete|--mirror|--tags)' \
    && deny "force-push, видалення гілок і пуш тегів робить лише власник."
  has 'push[^|;&]*[[:space:]:](main|master)([[:space:]]|\\|"|$)' \
    && deny "пуш у main. Зміни потрапляють у main лише через pull request."
  case "$branch" in
    main|master) deny "пуш із гілки '$branch'. Перейди на робочу гілку." ;;
  esac
fi

has 'git[[:space:]]+(-[^[:space:]]+[[:space:]]+)*tag[[:space:]]+(-a|-s|-f|v[0-9])' \
  && deny "теги релізів створює лише власник (docs/RELEASE.md)."

has '(build-release|create-keystore)\.sh' \
  && deny "скрипти релізу й ключа підпису запускає лише власник."

exit 0
