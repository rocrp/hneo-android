#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=scripts/android-env.sh
source "$(dirname "$0")/android-env.sh"

[[ "$(uname -s)" == Darwin ]] || fail "Automatic setup supports macOS; install JDK 17 and the Android CLI manually on other systems."
command -v brew >/dev/null 2>&1 || fail "Install Homebrew first: https://brew.sh"

for formula in just openjdk@17; do
    if ! brew list --formula "$formula" >/dev/null 2>&1; then
        brew install "$formula"
    fi
done
for cask in android-commandlinetools android-studio; do
    if ! brew list --cask "$cask" >/dev/null 2>&1; then
        brew install --cask "$cask"
    fi
done

configure_android_env
require_command android
mkdir -p "$ANDROID_HOME"
android --sdk="$ANDROID_HOME" init
android --sdk="$ANDROID_HOME" sdk install platform-tools platforms/android-35 build-tools/35.0.0 sources/android-35
validate_sdk

# Java properties require escaped backslashes; protect SDK paths containing spaces.
sdk_property="${ANDROID_HOME//\\/\\\\}"
sdk_property="${sdk_property// /\\ }"
printf 'sdk.dir=%s\n' "$sdk_property" > "$PROJECT_ROOT/local.properties"

# Studio's bundled runtime can be newer than this Gradle wrapper supports.
# GRADLE_LOCAL_JAVA_HOME resolves this ignored, machine-local JDK selection.
mkdir -p "$PROJECT_ROOT/.gradle" "$PROJECT_ROOT/.idea"
java_property="${JAVA_HOME//\\/\\\\}"
java_property="${java_property// /\\ }"
printf 'java.home=%s\n' "$java_property" > "$PROJECT_ROOT/.gradle/config.properties"
if [[ ! -f "$PROJECT_ROOT/.idea/gradle.xml" ]]; then
    cat > "$PROJECT_ROOT/.idea/gradle.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="GradleSettings">
    <option name="linkedExternalProjectsSettings">
      <GradleProjectSettings>
        <option name="externalProjectPath" value="$PROJECT_DIR$" />
        <option name="gradleJvm" value="#GRADLE_LOCAL_JAVA_HOME" />
        <option name="modules">
          <set>
            <option value="$PROJECT_DIR$" />
            <option value="$PROJECT_DIR$/app" />
          </set>
        </option>
      </GradleProjectSettings>
    </option>
  </component>
</project>
XML
fi
printf 'SDK: %s\nJDK 17: %s\nConfigured local.properties. Next: just doctor, then just check.\n' "$ANDROID_HOME" "$JAVA_HOME"
