# shellcheck shell=bash
# Shared helpers for the owner-run release scripts (create-keystore.sh, build-release.sh).
# Works in Git Bash on Windows, and in bash on macOS and Linux. Source it; do not run it.

# Path for display to a human: Windows form in Git Bash, unchanged elsewhere.
display_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}

# Path handed to a Windows program (Java, Gradle): C:/x/y form in Git Bash, unchanged elsewhere.
native_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}

# Converts a Windows path from an environment variable into a Git Bash path.
unix_path() {
  if [ -n "$1" ] && command -v cygpath >/dev/null 2>&1; then cygpath -u "$1"; else echo "$1"; fi
}

# Executable inside a directory, with or without .exe.
exe() { # exe <dir> <name>
  if [ -x "$1/$2" ]; then echo "$1/$2"; elif [ -x "$1/$2.exe" ]; then echo "$1/$2.exe"; else return 1; fi
}

# Major Java version of a JDK home (8, 17, 21...), empty if it is not a working JDK with keytool.
java_major() {
  local java version
  java="$(exe "$1/bin" java)" || return 1
  exe "$1/bin" keytool >/dev/null || return 1
  version="$("$java" -version 2>&1 | awk -F'"' '/version "/ {print $2; exit}')"
  [ -n "$version" ] || return 1
  case "$version" in 1.*) echo "${version#1.}" | cut -d. -f1 ;; *) echo "$version" | cut -d. -f1 | cut -d- -f1 ;; esac
}

# Prints the home of the first JDK >= $1 (default 17): JAVA_HOME, the `java` on PATH,
# Android Studio's bundled runtime, then common JDK install folders on Windows/macOS/Linux.
find_java_home() {
  local min="${1:-17}" candidates=() c major local_app
  [ -n "${JAVA_HOME:-}" ] && candidates+=("$(unix_path "$JAVA_HOME")")
  if command -v java >/dev/null 2>&1; then
    c="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.home =/ {print $2; exit}' | tr -d '\r')"
    [ -n "$c" ] && candidates+=("$(unix_path "$c")")
  fi
  local_app="$(unix_path "${LOCALAPPDATA:-}")"
  candidates+=(
    "/c/Program Files/Android/Android Studio/jbr"
    "$local_app/Programs/Android Studio/jbr"
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "/opt/android-studio/jbr"
    "$HOME/android-studio/jbr"
  )
  for c in "$local_app/JetBrains/Toolbox/apps/"*/*/jbr \
           "$HOME/Library/Application Support/JetBrains/Toolbox/apps/"*/*/jbr/Contents/Home \
           "/c/Program Files/Eclipse Adoptium/"* \
           "/c/Program Files/Java/"* \
           "/c/Program Files/Microsoft/"jdk* \
           "/c/Program Files/Zulu/"* \
           /Library/Java/JavaVirtualMachines/*/Contents/Home \
           /usr/lib/jvm/*; do
    candidates+=("$c")
  done
  for c in "${candidates[@]}"; do
    [ -d "$c" ] || continue
    major="$(java_major "$c" 2>/dev/null)" || continue
    if [ -n "$major" ] && [ "$major" -ge "$min" ] 2>/dev/null; then echo "$c"; return 0; fi
  done
  return 1
}

# Prints the Android SDK folder: ANDROID_HOME, ANDROID_SDK_ROOT, sdk.dir in local.properties of
# repository $1, then the Android Studio defaults on Windows/macOS/Linux.
find_android_sdk() {
  local repo="${1:-.}" candidates=() c
  [ -n "${ANDROID_HOME:-}" ] && candidates+=("$(unix_path "$ANDROID_HOME")")
  [ -n "${ANDROID_SDK_ROOT:-}" ] && candidates+=("$(unix_path "$ANDROID_SDK_ROOT")")
  if [ -f "$repo/local.properties" ]; then
    # sdk.dir=C\:\\Users\\me\\AppData\\Local\\Android\\Sdk (escaped Java properties)
    c="$(sed -n 's/^sdk\.dir=//p' "$repo/local.properties" | tail -n 1 | sed 's/\\:/:/g; s/\\\\/\\/g' | tr -d '\r')"
    [ -n "$c" ] && candidates+=("$(unix_path "$c")")
  fi
  candidates+=(
    "$(unix_path "${LOCALAPPDATA:-}")/Android/Sdk"
    "$HOME/Library/Android/sdk"
    "$HOME/Android/Sdk"
  )
  for c in "${candidates[@]}"; do
    if [ -n "$c" ] && { [ -d "$c/platform-tools" ] || [ -d "$c/build-tools" ] || [ -d "$c/platforms" ]; }; then
      echo "$c"
      return 0
    fi
  done
  return 1
}

# Fingerprint (lowercase hex, no colons) of the certificate of $alias in a keystore.
# Password comes from the environment variable named in $3 (keytool -storepass:env).
keystore_cert_sha256() { # keystore_cert_sha256 <keytool> <keystore> <password-env-var> <alias>
  "$1" -J-Duser.language=en -list -v -keystore "$(native_path "$2")" -alias "$4" -storepass:env "$3" 2>/dev/null \
    | awk '/SHA256:/ {print $2; exit}' | tr -d ':\r' | tr 'A-F' 'a-f'
}
