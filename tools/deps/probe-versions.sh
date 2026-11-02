#!/usr/bin/env bash
# Prints the latest stable and latest overall versions of the project's key
# dependencies straight from Maven Central / Google Maven / Gradle services.
# Used before pinning versions in gradle/libs.versions.toml (spec §3.3.1, ADR-001).
# Output is Markdown so CI can post it into a PR comment.
set -uo pipefail

CENTRAL="https://repo1.maven.org/maven2"
GOOGLE="https://dl.google.com/dl/android/maven2"

UNSTABLE='(alpha|beta|rc|dev|eap|snapshot|preview|pre|-m[0-9]|-M[0-9])'

meta() { # repo groupPath artifact -> all versions, one per line
  curl -fsSL "$1/$2/$3/maven-metadata.xml" 2>/dev/null \
    | grep -o '<version>[^<]*</version>' | sed -E 's#</?version>##g'
}

row() { # label repo group artifact
  local label="$1" repo="$2" group="$3" artifact="$4" path all stable latest
  path="${group//./\/}"
  all="$(meta "$repo" "$path" "$artifact")"
  if [[ -z "$all" ]]; then
    echo "| $label | \`$group:$artifact\` | — | — | not found |"
    return
  fi
  stable="$(echo "$all" | grep -Eiv "$UNSTABLE" | sort -V | tail -1)"
  latest="$(echo "$all" | sort -V | tail -1)"
  echo "| $label | \`$group:$artifact\` | ${stable:-—} | $latest | ok |"
}

module_deps() { # repo group artifact version -> dependencies from Gradle module metadata
  local repo="$1" group="$2" artifact="$3" version="$4" path
  path="${group//./\/}"
  curl -fsSL "$repo/$path/$artifact/$version/$artifact-$version.module" 2>/dev/null \
    | jq -r '[.variants[]? | .dependencies[]? | "\(.group):\(.module):\(.version.requires // .version.strictly // "?")"] | unique | .[]' 2>/dev/null \
    || echo "(module metadata unavailable)"
}

echo "## Dependency version probe"
echo
echo "Generated: $(date -u +%Y-%m-%dT%H:%MZ)"
echo
echo "| Component | Coordinates | Latest stable | Latest any | Status |"
echo "| --- | --- | --- | --- | --- |"
row "Kotlin Gradle plugin" "$CENTRAL" org.jetbrains.kotlin kotlin-gradle-plugin
row "KSP" "$CENTRAL" com.google.devtools.ksp com.google.devtools.ksp.gradle.plugin
row "AGP" "$GOOGLE" com.android.tools.build gradle
row "Compose MP plugin" "$CENTRAL" org.jetbrains.compose compose-gradle-plugin
row "Compose MP runtime" "$CENTRAL" org.jetbrains.compose.runtime runtime
row "Compose MP material3" "$CENTRAL" org.jetbrains.compose.material3 material3
row "Compose MP lifecycle VM" "$CENTRAL" org.jetbrains.androidx.lifecycle lifecycle-viewmodel-compose
row "Compose MP navigation" "$CENTRAL" org.jetbrains.androidx.navigation navigation-compose
row "Room 3 runtime" "$GOOGLE" androidx.room3 room3-runtime
row "Room 3 compiler" "$GOOGLE" androidx.room3 room3-compiler
row "Room 3 Gradle plugin" "$GOOGLE" androidx.room3 room3-gradle-plugin
row "SQLite bundled" "$GOOGLE" androidx.sqlite sqlite-bundled
row "SQLite web" "$GOOGLE" androidx.sqlite sqlite-web
row "Activity Compose" "$GOOGLE" androidx.activity activity-compose
row "AndroidX test runner" "$GOOGLE" androidx.test runner
row "AndroidX test ext junit" "$GOOGLE" androidx.test.ext junit
row "Koin core" "$CENTRAL" io.insert-koin koin-core
row "Koin compose VM" "$CENTRAL" io.insert-koin koin-compose-viewmodel
row "Koin Android" "$CENTRAL" io.insert-koin koin-android
row "Coroutines" "$CENTRAL" org.jetbrains.kotlinx kotlinx-coroutines-core
row "Datetime" "$CENTRAL" org.jetbrains.kotlinx kotlinx-datetime
row "Serialization JSON" "$CENTRAL" org.jetbrains.kotlinx kotlinx-serialization-json
row "supabase-kt BOM" "$CENTRAL" io.github.jan-tennert.supabase bom
row "Ktor client core" "$CENTRAL" io.ktor ktor-client-core
row "Turbine" "$CENTRAL" app.cash.turbine turbine
row "Kotest property" "$CENTRAL" io.kotest kotest-property
row "JUnit 4" "$CENTRAL" junit junit
# QA tools (spec §12.1: versions are pinned in Ф0).
row "Roborazzi" "$CENTRAL" io.github.takahirom.roborazzi roborazzi
row "Roborazzi Compose" "$CENTRAL" io.github.takahirom.roborazzi roborazzi-compose
row "Roborazzi Compose Desktop" "$CENTRAL" io.github.takahirom.roborazzi roborazzi-compose-desktop
row "Roborazzi Gradle plugin" "$CENTRAL" io.github.takahirom.roborazzi roborazzi-gradle-plugin
row "Robolectric" "$CENTRAL" org.robolectric robolectric
row "Compose MP ui-test" "$CENTRAL" org.jetbrains.compose.ui ui-test
row "AndroidX Compose ui-test-junit4" "$GOOGLE" androidx.compose.ui ui-test-junit4
row "AndroidX Compose ui-test-manifest" "$GOOGLE" androidx.compose.ui ui-test-manifest
echo

# Tools shipped as GitHub releases (CLI binaries, images).
release_row() { # label owner/repo
  local tag date
  tag="$(gh api "repos/$2/releases/latest" --jq '.tag_name' 2>/dev/null)"
  date="$(gh api "repos/$2/releases/latest" --jq '.published_at[0:10]' 2>/dev/null)"
  echo "| $1 | \`$2\` | ${tag:-—} | ${date:-—} |"
}
echo "| Tool | GitHub repository | Latest release | Published |"
echo "| --- | --- | --- | --- |"
release_row "Maestro CLI" mobile-dev-inc/maestro
release_row "Toxiproxy" Shopify/toxiproxy
release_row "Supabase CLI" supabase/cli
release_row "pgTAP" theory/pgtap
echo

GRADLE_CURRENT="$(curl -fsSL https://services.gradle.org/versions/current 2>/dev/null | jq -r '.version // empty')"
echo "**Gradle current:** ${GRADLE_CURRENT:-unknown}"
if [[ -n "$GRADLE_CURRENT" ]]; then
  SHA="$(curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_CURRENT}-bin.zip.sha256" 2>/dev/null)"
  echo "**gradle-${GRADLE_CURRENT}-bin.zip sha256:** \`${SHA:-unknown}\`"
fi
for v in ${EXTRA_GRADLE_VERSIONS:-}; do
  SHA="$(curl -fsSL "https://services.gradle.org/distributions/gradle-${v}-bin.zip.sha256" 2>/dev/null)"
  echo "**gradle-${v}-bin.zip sha256:** \`${SHA:-not found}\`"
done
echo

for spec in ${MODULE_DEPS:-}; do
  IFS=: read -r repo_key group artifact version <<<"$spec"
  repo="$CENTRAL"; [[ "$repo_key" == google ]] && repo="$GOOGLE"
  echo "<details><summary>Dependencies of <code>$group:$artifact:$version</code></summary>"
  echo
  echo '```'
  module_deps "$repo" "$group" "$artifact" "$version"
  echo '```'
  echo "</details>"
  echo
done
