#!/usr/bin/env bash
# Builds a signed release APK on the owner's computer — the primary release path (ADR-003).
#
#   bash tools/release/build-release.sh [X.Y.Z]      (double-click works too: it asks for the version)
#
# 1. Checks: the checkout is exactly origin/main with no local changes; the version is new
#    (or a rebuild of an existing tag on this very commit).
# 2. Asks the key password and checks the key against .github/release-signing-cert.sha256
#    before spending time on the build.
# 3. Gradle assembleRelease with versionName = X.Y.Z, versionCode = X*1000000 + Y*1000 + Z.
# 4. Verifies the APK: signer certificate, versionCode/versionName, arm64-v8a only.
# 5. Writes dist/todo-vX.Y.Z.apk and .sha256 (dist/ is git-ignored).
# 6. Creates tag vX.Y.Z and offers to push it (GitHub then builds the backup copy in Releases).
# 7. Offers `adb install -r` when a phone is connected over USB; otherwise explains how to send
#    the file to the phone. Always an install OVER the previous version — never uninstall first.
#
# Environment (all optional):
#   TODO_RELEASE_KEYSTORE            keystore (default: ~/todo-release-key/todo-release.jks)
#   TODO_RELEASE_KEY_ALIAS           alias (default: todo-release)
#   TODO_RELEASE_KEYSTORE_PASSWORD   password; asked with hidden input when not set
#   TODO_RELEASE_TEST=1              CI only: skips git/version checks, tag and install; the expected
#                                    certificate comes from TODO_EXPECTED_CERT_SHA256 and a summary is
#                                    written to TODO_RELEASE_EVIDENCE
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=tools/release/common.sh
. "$SCRIPT_DIR/common.sh"
cd "$REPO_ROOT" || exit 1

TEST_MODE="${TODO_RELEASE_TEST:-0}"
ALIAS="${TODO_RELEASE_KEY_ALIAS:-todo-release}"
PINNED_FILE=".github/release-signing-cert.sha256"

finish() {
  local status=$?
  echo
  if [ "$status" -eq 0 ]; then
    echo "Готово."
  else
    echo "Збірку зупинено (код $status). Нічого не встановлено й не опубліковано — див. повідомлення вище."
  fi
  if [ -t 0 ] && [ "$TEST_MODE" != 1 ]; then
    read -r -p "Натисни Enter, щоб закрити вікно..." _ || true
  fi
}
trap finish EXIT

fail() {
  echo
  echo "ПОМИЛКА: $*" >&2
  exit 1
}

ask_yes() { # ask_yes <question>; default yes; "no" when there is no terminal
  local answer
  [ -t 0 ] || return 1
  read -r -p "$1 [Y/n] " answer || return 1
  case "$answer" in [nNнН]*) return 1 ;; *) return 0 ;; esac
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi
}

echo "== Збірка релізу To-do app"
echo "Репозиторій: $(display_path "$REPO_ROOT")"
echo

# --- 1. git state and version ---------------------------------------------------------------------
HEAD_SHA=""
EXISTING_TAG=0
if [ "$TEST_MODE" != 1 ]; then
  command -v git >/dev/null 2>&1 || fail "git не знайдено. На Windows запускай скрипт у Git Bash."
  echo "Отримую останній стан з GitHub..."
  git fetch --quiet origin main || fail "Не вдалося отримати origin/main (перевір інтернет і вхід у GitHub)."
  git fetch --quiet --tags origin 2>/dev/null || true
  HEAD_SHA="$(git rev-parse HEAD)"
  MAIN_SHA="$(git rev-parse origin/main)"
  if [ "$HEAD_SHA" != "$MAIN_SHA" ]; then
    fail "Поточний коміт ${HEAD_SHA:0:7} ($(git rev-parse --abbrev-ref HEAD)) не збігається з origin/main ${MAIN_SHA:0:7}.
Реліз збирається лише з main. Виконай: git checkout main && git pull"
  fi
  if ! git diff --quiet || ! git diff --cached --quiet; then
    fail "Є незакомічені зміни у файлах репозиторію (git status). Реліз має відповідати коду в main."
  fi
fi

LAST_TAG=""
[ "$TEST_MODE" != 1 ] && LAST_TAG="$(git tag -l 'v*.*.*' --sort=-v:refname | head -n 1)"
VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  [ "$TEST_MODE" = 1 ] && fail "У тестовому режимі версію передають аргументом."
  read -r -p "Версія релізу, наприклад 0.0.2 (остання: ${LAST_TAG:-ще немає}): " VERSION || fail "Версію не введено."
fi
VERSION="${VERSION#v}"
if [[ ! "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  fail "Версія '$VERSION' не має вигляду X.Y.Z (наприклад 0.0.2)."
fi
MAJOR=$((10#${BASH_REMATCH[1]})); MINOR=$((10#${BASH_REMATCH[2]})); PATCH=$((10#${BASH_REMATCH[3]}))
if (( MINOR > 999 || PATCH > 999 || MAJOR > 2000 )); then
  fail "Частини версії мають бути не більші за 2000.999.999."
fi
VERSION="$MAJOR.$MINOR.$PATCH"
CODE=$(( MAJOR * 1000000 + MINOR * 1000 + PATCH ))
(( CODE > 0 )) || fail "Версія 0.0.0 неможлива: Android вимагає versionCode більший за 0."
TAG="v$VERSION"

if [ "$TEST_MODE" != 1 ]; then
  if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    TAG_SHA="$(git rev-list -n 1 "$TAG")"
    [ "$TAG_SHA" = "$HEAD_SHA" ] || fail "Тег $TAG уже існує на іншому коміті (${TAG_SHA:0:7}). Обери нову версію."
    EXISTING_TAG=1
    echo "Тег $TAG уже стоїть на цьому коміті: перезбираю ту саму версію."
  elif [ -n "$LAST_TAG" ] && [ "$(printf '%s\n%s\n' "$LAST_TAG" "$TAG" | sort -V | tail -n 1)" != "$TAG" ]; then
    fail "$TAG не новіша за останній реліз $LAST_TAG. Телефон не встановить старішу версію поверх новішої."
  fi
fi
echo "Версія: $VERSION (versionCode $CODE)"

# --- 2. tools and key --------------------------------------------------------------------------------
JDK_HOME="$(find_java_home 17)" || fail "Не знайдено JDK 17+ для Gradle.
Встанови Android Studio (developer.android.com/studio) — у неї вбудований JDK — і запусти скрипт знову.
Або вкажи JDK вручну: JAVA_HOME=/шлях/до/jdk bash tools/release/build-release.sh"
SDK="$(find_android_sdk "$REPO_ROOT")" || fail "Не знайдено Android SDK.
Встанови Android Studio і відкрий її один раз: майстер першого запуску завантажить SDK.
Або вкажи шлях: ANDROID_HOME=/шлях/до/Android/Sdk bash tools/release/build-release.sh"
KEYTOOL="$(exe "$JDK_HOME/bin" keytool)"
JAVA="$(exe "$JDK_HOME/bin" java)"
echo "JDK: $(display_path "$JDK_HOME") (Java $(java_major "$JDK_HOME"))"
echo "Android SDK: $(display_path "$SDK")"

if [ "$TEST_MODE" = 1 ]; then
  EXPECTED="${TODO_EXPECTED_CERT_SHA256:?TODO_EXPECTED_CERT_SHA256 must be set in test mode}"
else
  EXPECTED="$(grep -Eo '^[0-9a-f]{64}$' "$PINNED_FILE" 2>/dev/null | head -n 1)"
  [ -n "$EXPECTED" ] || fail "У $PINNED_FILE немає відбитка сертифіката (docs/RELEASE.md, крок 3)."
fi

KEYSTORE="$(unix_path "${TODO_RELEASE_KEYSTORE:-$HOME/todo-release-key/todo-release.jks}")"
[ -f "$KEYSTORE" ] || fail "Ключ не знайдено: $(display_path "$KEYSTORE")
Він створюється скриптом tools/release/create-keystore.sh у ~/todo-release-key.
Якщо ключ на іншому комп'ютері — віднови його з резервної копії в цю папку
або вкажи шлях: TODO_RELEASE_KEYSTORE=/шлях/todo-release.jks bash tools/release/build-release.sh"
echo "Ключ: $(display_path "$KEYSTORE") (alias $ALIAS)"

if [ -z "${TODO_RELEASE_KEYSTORE_PASSWORD:-}" ]; then
  read -r -s -p "Пароль ключа (символи не показуються): " TODO_RELEASE_KEYSTORE_PASSWORD || fail "Пароль не введено."
  echo
fi
export TODO_RELEASE_KEYSTORE_PASSWORD
ACTUAL_KEY="$(keystore_cert_sha256 "$KEYTOOL" "$KEYSTORE" TODO_RELEASE_KEYSTORE_PASSWORD "$ALIAS")"
[ -n "$ACTUAL_KEY" ] || fail "Не вдалося відкрити ключ: неправильний пароль або alias '$ALIAS'."
[ "$ACTUAL_KEY" = "$EXPECTED" ] || fail "Цей ключ не той, що закріплений у $PINNED_FILE.
  ключ:       $ACTUAL_KEY
  закріплено: $EXPECTED
APK, підписаний іншим ключем, не встановиться поверх наявного застосунку."
echo "Ключ збігається із закріпленим сертифікатом."

# --- 3. build -----------------------------------------------------------------------------------------
export JAVA_HOME
JAVA_HOME="$(native_path "$JDK_HOME")"
export ANDROID_HOME
ANDROID_HOME="$(native_path "$SDK")"
export TODO_RELEASE_KEYSTORE
TODO_RELEASE_KEYSTORE="$(native_path "$KEYSTORE")"
export TODO_RELEASE_KEY_ALIAS="$ALIAS"
export TODO_RELEASE_KEY_PASSWORD="$TODO_RELEASE_KEYSTORE_PASSWORD"

mkdir -p dist
BUILD_LOG="dist/build-$TAG.log"
echo
echo "Збираю APK. Перша збірка на комп'ютері завантажує залежності й може тривати 10–15 хвилин."
bash ./gradlew --console=plain :androidApp:assembleRelease \
  -PversionName="$VERSION" -PversionCode="$CODE" 2>&1 | tee "$BUILD_LOG"
BUILD_STATUS=${PIPESTATUS[0]}
unset TODO_RELEASE_KEYSTORE_PASSWORD TODO_RELEASE_KEY_PASSWORD
[ "$BUILD_STATUS" -eq 0 ] || fail "Gradle-збірка не вдалася (код $BUILD_STATUS). Повний лог: $(display_path "$REPO_ROOT/$BUILD_LOG")"

# --- 4. verify ----------------------------------------------------------------------------------------
APK="androidApp/build/outputs/apk/release/androidApp-release.apk"
[ -f "$APK" ] || fail "Gradle не створив $APK"
BUILD_TOOLS="$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)"
[ -n "$BUILD_TOOLS" ] || fail "У $(display_path "$SDK") немає build-tools для перевірки підпису."
AAPT2="$(exe "$BUILD_TOOLS" aapt2)" || fail "Не знайдено aapt2 у $(display_path "$BUILD_TOOLS")"

SIGNER="$("$JAVA" -jar "$(native_path "$BUILD_TOOLS/lib/apksigner.jar")" verify --print-certs "$(native_path "$APK")" 2>&1)" \
  || fail "apksigner: APK не пройшов перевірку підпису.
$SIGNER"
APK_CERT="$(echo "$SIGNER" | grep -E 'certificate SHA-256 digest: ' | head -n 1 | awk '{print $NF}' | tr -d '\r')"
BADGING="$("$AAPT2" dump badging "$(native_path "$APK")" 2>/dev/null | tr -d '\r')"
APK_CODE="$(echo "$BADGING" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -n 1)"
APK_NAME="$(echo "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -n 1)"
APK_ABIS="$(echo "$BADGING" | sed -n "s/^native-code: //p" | tr -d "'")"

[ "$APK_CERT" = "$EXPECTED" ] || fail "APK підписаний сертифікатом '$APK_CERT', очікувався $EXPECTED."
[ "$APK_CODE" = "$CODE" ] || fail "У APK versionCode=$APK_CODE, очікувався $CODE."
[ "$APK_NAME" = "$VERSION" ] || fail "У APK versionName=$APK_NAME, очікувався $VERSION."
[ "$APK_ABIS" = "arm64-v8a" ] || fail "У APK ABI '$APK_ABIS', очікувався лише arm64-v8a."

# --- 5. output ----------------------------------------------------------------------------------------
OUT="dist/todo-$TAG.apk"
cp "$APK" "$OUT" || fail "Не вдалося скопіювати APK у $OUT"
APK_SHA="$(sha256_of "$OUT")"
echo "$APK_SHA  todo-$TAG.apk" > "$OUT.sha256"
SIZE_KIB=$(( $(wc -c < "$OUT") / 1024 ))

echo
echo "APK перевірено:"
echo "  файл:        $(display_path "$REPO_ROOT/$OUT") ($SIZE_KIB KiB)"
echo "  версія:      $APK_NAME (versionCode $APK_CODE), ABI $APK_ABIS"
echo "  підпис:      сертифікат збігається із закріпленим"
echo "  sha256 APK:  $APK_SHA"

if [ "$TEST_MODE" = 1 ]; then
  if [ -n "${TODO_RELEASE_EVIDENCE:-}" ]; then
    {
      echo "local release script: $(uname -s) / Java $(java_major "$JDK_HOME")"
      echo "release APK: $SIZE_KIB KiB, versionName=$APK_NAME versionCode=$APK_CODE, ABIs: $APK_ABIS"
      echo "$SIGNER" | grep -E '^Verified using v[0-9].*true' | tr -d '\r'
      echo "signer certificate matches the expected key: yes"
    } > "$TODO_RELEASE_EVIDENCE"
  fi
  exit 0
fi

# --- 6. tag ---------------------------------------------------------------------------------------------
echo
if [ "$EXISTING_TAG" = 1 ]; then
  echo "Тег $TAG уже існує, новий не створюю."
else
  git tag -a "$TAG" -m "$TAG" "$HEAD_SHA" || fail "Не вдалося створити тег $TAG (APK уже зібрано: $OUT)."
  echo "Створено тег $TAG локально."
  if ask_yes "Відправити тег $TAG на GitHub? Це зафіксує версію, а GitHub збере резервну копію APK у Releases."; then
    if git push origin "$TAG"; then
      echo "Тег відправлено. Резервна копія з'явиться на сторінці Releases приблизно за 15 хвилин."
    else
      echo "Тег не вдалося відправити. Пізніше: git push origin $TAG"
    fi
  else
    echo "Тег лишився локальним. Відправити пізніше: git push origin $TAG"
  fi
fi

# --- 7. phone -------------------------------------------------------------------------------------------
echo
ADB="$(exe "$SDK/platform-tools" adb 2>/dev/null || true)"
INSTALLED=0
if [ -n "$ADB" ] && "$ADB" devices 2>/dev/null | tr -d '\r' | grep -qE $'\tdevice$'; then
  if ask_yes "Телефон підключено через USB. Встановити $TAG поверх наявної версії?"; then
    INSTALL_OUT="$("$ADB" install -r "$(native_path "$OUT")" 2>&1 | tr -d '\r')"
    echo "$INSTALL_OUT"
    if echo "$INSTALL_OUT" | grep -q '^Success'; then
      INSTALLED=1
      echo "Встановлено на телефон."
    elif echo "$INSTALL_OUT" | grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE'; then
      echo "Телефон відмовив: встановлений застосунок підписаний іншим ключем. НЕ видаляй його — спершу зроби backup даних."
    elif echo "$INSTALL_OUT" | grep -q 'INSTALL_FAILED_VERSION_DOWNGRADE'; then
      echo "Телефон відмовив: на ньому вже новіша версія."
    fi
  fi
fi
if [ "$INSTALLED" != 1 ]; then
  echo "Як поставити на телефон (завжди поверх старої версії, нічого не видаляючи):"
  echo "  - надішли собі файл $(basename "$OUT") (Telegram «Збережене», Google Drive, кабель USB);"
  echo "  - відкрий його на телефоні → «Встановити»/«Оновити»;"
  echo "  - уперше Android попросить дозволити встановлення з цього застосунку — дозволь."
fi

if [ -t 0 ]; then
  if command -v cygpath >/dev/null 2>&1; then
    explorer.exe "$(cygpath -w "$REPO_ROOT/dist")" >/dev/null 2>&1 || true
  elif command -v open >/dev/null 2>&1 && [ "$(uname -s)" = "Darwin" ]; then
    open "$REPO_ROOT/dist" >/dev/null 2>&1 || true
  fi
fi
exit 0
