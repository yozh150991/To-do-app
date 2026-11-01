#!/usr/bin/env bash
# Builds a release APK signed with a throwaway key and verifies the signature, ABIs and version.
# Proves the release signing configuration on every PR without the real key (docs/RELEASE.md).
set -euo pipefail
WORK="$(mktemp -d)"
KEYSTORE="$WORK/throwaway.jks"
PASSWORD="throwaway-$RANDOM-$RANDOM" # not a secret: the key is discarded with the runner
EVIDENCE_DIR="androidApp/build/reports/runtime-evidence"
mkdir -p "$EVIDENCE_DIR"

keytool -genkeypair -noprompt -keystore "$KEYSTORE" -storetype PKCS12 -alias ci -keyalg RSA -keysize 2048 \
  -validity 2 -dname "CN=CI throwaway key" -storepass "$PASSWORD" -keypass "$PASSWORD" >/dev/null 2>&1

TODO_RELEASE_KEYSTORE="$KEYSTORE" \
TODO_RELEASE_KEYSTORE_PASSWORD="$PASSWORD" \
TODO_RELEASE_KEY_ALIAS=ci \
TODO_RELEASE_KEY_PASSWORD="$PASSWORD" \
  ./gradlew --stacktrace :androidApp:assembleRelease -PversionName=0.0.0-ci -PversionCode=1

APK="androidApp/build/outputs/apk/release/androidApp-release.apk"
BUILD_TOOLS="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -n 1)"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK" > "$WORK/apksigner.txt"

EXPECTED="$(keytool -list -v -keystore "$KEYSTORE" -storepass "$PASSWORD" -alias ci \
  | awk '/SHA256:/ {print $2}' | tr -d ':' | tr 'A-F' 'a-f')"
ACTUAL="$(awk -F': ' '/Signer #1 certificate SHA-256 digest/ {print $2}' "$WORK/apksigner.txt")"
ABIS="$(unzip -l "$APK" | awk '{print $4}' | grep '^lib/' | cut -d/ -f2 | sort -u | tr '\n' ' ')"
BADGING="$("$BUILD_TOOLS/aapt2" dump badging "$APK" | head -n 1)"

{
  echo "release APK: $(du -k "$APK" | cut -f1) KiB ($(stat -c %s "$APK") bytes)"
  echo "ABIs in APK: ${ABIS:-none}"
  echo "$BADGING"
  grep -E "^Verified using v[0-9]" "$WORK/apksigner.txt"
  echo "signer certificate matches the build key: $([ "$EXPECTED" = "$ACTUAL" ] && echo yes || echo NO)"
} | tee "$EVIDENCE_DIR/release-apk.txt"

rm -rf "$WORK"
[ -n "$ACTUAL" ] && [ "$EXPECTED" = "$ACTUAL" ]
[ "$ABIS" = "arm64-v8a " ]
