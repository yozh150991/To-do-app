#!/usr/bin/env bash
# Creates the release signing key ONCE, on the owner's computer (spec §3.4, docs/RELEASE.md).
#
# Works when double-clicked (Windows: Git Bash; macOS/Linux: a terminal) and when run from a shell:
#   bash tools/release/create-keystore.sh [output-folder]
#
# The key is written to a folder OUTSIDE the repository (default: ~/todo-release-key), whatever the
# current directory is. The window stays open at the end so every message can be read.
#
# Output:
#   todo-release.jks              the keystore: back it up to TWO independent places
#   todo-release.jks.base64.txt   single-line base64 for the GitHub secret; delete after use
#   certificate-sha256.txt        fingerprint for .github/release-signing-cert.sha256
#
# Passwords are read by this script (hidden input) and handed to keytool through an environment
# variable (`-storepass:env`), so keytool never prompts: its own prompts do not work in Git Bash.
set -uo pipefail

ALIAS="todo-release"
KEYSTORE="todo-release.jks"
MIN_PASSWORD_LENGTH=12

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=tools/release/common.sh
. "$SCRIPT_DIR/common.sh"

# --- keep the window open -------------------------------------------------------------------------
finish() {
  local status=$?
  echo
  if [ "$status" -eq 0 ]; then
    echo "Готово."
  else
    echo "Скрипт зупинився з помилкою (код $status). Ключ НЕ створено або створено не повністю — див. повідомлення вище."
  fi
  if [ -t 0 ]; then
    read -r -p "Натисни Enter, щоб закрити вікно..." _ || true
  fi
}
trap finish EXIT

fail() {
  echo
  echo "ПОМИЛКА: $*" >&2
  exit 1
}

# --- where to write -------------------------------------------------------------------------------
OUT_DIR="${1:-$HOME/todo-release-key}"
mkdir -p "$OUT_DIR" || fail "Не вдалося створити папку $OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
cd "$OUT_DIR" || fail "Не вдалося перейти в папку $OUT_DIR"

if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  fail "Папка $OUT_DIR знаходиться всередині git-репозиторію. Вкажи іншу: bash create-keystore.sh <папка>"
fi
if [ -e "$KEYSTORE" ]; then
  fail "У $OUT_DIR уже є $KEYSTORE. Ключ створюється один раз; існуючий не перезаписую."
fi

echo "== Створення ключа підпису To-do app"
echo "Папка для ключа: $(display_path "$OUT_DIR")"
echo

JDK_HOME="$(find_java_home 11)" || fail "Не знайдено робочий keytool.
Він є в Android Studio або в будь-якому JDK. Варіанти:
  - встанови Android Studio (developer.android.com/studio) і запусти скрипт знову;
  - або встанови JDK 21 (adoptium.net, Temurin) і запусти скрипт знову;
  - або вкажи JDK вручну: JAVA_HOME=/шлях/до/jdk bash create-keystore.sh"
KEYTOOL="$(exe "$JDK_HOME/bin" keytool)"
echo "keytool: $(display_path "$KEYTOOL")"
echo

# --- password ---------------------------------------------------------------------------------
echo "Придумай пароль у менеджері паролів (щонайменше $MIN_PASSWORD_LENGTH символів) і встав його сюди."
echo "Під час введення символи не показуються — це нормально."
while true; do
  read -r -s -p "Пароль: " PASSWORD_1 || fail "Не вдалося прочитати пароль"
  echo
  read -r -s -p "Ще раз: " PASSWORD_2 || fail "Не вдалося прочитати пароль"
  echo
  if [ "$PASSWORD_1" != "$PASSWORD_2" ]; then
    echo "Паролі не збігаються, спробуй ще раз."
  elif [ "${#PASSWORD_1}" -lt "$MIN_PASSWORD_LENGTH" ]; then
    echo "Пароль коротший за $MIN_PASSWORD_LENGTH символів, спробуй ще раз."
  else
    break
  fi
done
export TODO_KEYSTORE_PASSWORD="$PASSWORD_1"
unset PASSWORD_1 PASSWORD_2

# --- create, verify, export -------------------------------------------------------------------------
echo
echo "Створюю ключ (RSA 4096, alias $ALIAS)..."
"$KEYTOOL" -J-Duser.language=en -genkeypair -noprompt \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=To-do app release, O=Personal" \
  -storepass:env TODO_KEYSTORE_PASSWORD -keypass:env TODO_KEYSTORE_PASSWORD \
  || fail "keytool не зміг створити ключ"

FINGERPRINT="$(keystore_cert_sha256 "$KEYTOOL" "$KEYSTORE" TODO_KEYSTORE_PASSWORD "$ALIAS")"
unset TODO_KEYSTORE_PASSWORD
[ "${#FINGERPRINT}" -eq 64 ] || fail "Ключ створено, але не вдалося прочитати його відбиток. Перевір: keytool -list -v -keystore $KEYSTORE"

base64 < "$KEYSTORE" | tr -d '\r\n' > "$KEYSTORE.base64.txt" || fail "Не вдалося створити $KEYSTORE.base64.txt"
echo "$FINGERPRINT" > certificate-sha256.txt

echo
echo "Створено в $(display_path "$OUT_DIR"):"
echo "  $KEYSTORE                  — сам ключ: зроби ДВІ незалежні копії (docs/RELEASE.md, крок 2)"
echo "  $KEYSTORE.base64.txt       — для secret TODO_RELEASE_KEYSTORE_BASE64; видали після кроку 4"
echo "  certificate-sha256.txt            — відбиток сертифіката"
echo
echo "Відбиток сертифіката (SHA-256) — надішли його в чат або встав у .github/release-signing-cert.sha256:"
echo
echo "  $FINGERPRINT"
echo
echo "Alias: $ALIAS. Пароль ключа = пароль keystore (той, що ти ввів)."
