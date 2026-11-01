#!/usr/bin/env bash
# Creates the release signing key ONCE, on the owner's computer (spec §3.4, docs/RELEASE.md).
# Needs `keytool` (bundled with any JDK or Android Studio: <Android Studio>/jbr/bin/keytool).
#
# Output (in the current directory, never inside the git repository):
#   todo-release.jks                 the keystore: back it up to TWO independent places
#   todo-release.jks.base64.txt      single-line base64 for the GitHub secret, delete after use
# and prints the certificate SHA-256 fingerprint for .github/release-signing-cert.sha256.
set -euo pipefail

KEYSTORE="todo-release.jks"
ALIAS="todo-release"

if git rev-parse --show-toplevel >/dev/null 2>&1; then
  echo "Run this outside the git repository (for example in an empty folder) so the key can never be committed." >&2
  exit 1
fi
if [ -e "$KEYSTORE" ]; then
  echo "$KEYSTORE already exists here; refusing to overwrite a signing key." >&2
  exit 1
fi
command -v keytool >/dev/null || { echo "keytool not found: install a JDK or use Android Studio's jbr/bin/keytool" >&2; exit 1; }

echo "keytool will now ask for a keystore password (twice). Use a long random password and store it"
echo "in your password manager together with the alias '$ALIAS'. For PKCS12 the key password is the same."
keytool -genkeypair -v \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=To-do app release, O=Personal"

base64 < "$KEYSTORE" | tr -d '\n' > "$KEYSTORE.base64.txt"

echo
echo "Certificate SHA-256 (put this line into .github/release-signing-cert.sha256):"
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" | awk '/SHA256:/ {print $2}' | tr -d ':' | tr 'A-F' 'a-f'
echo
echo "Next: docs/RELEASE.md, steps 2-4 (two backups, GitHub secrets, fingerprint PR)."
