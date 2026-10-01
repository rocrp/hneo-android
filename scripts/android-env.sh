#!/usr/bin/env bash
# Shared environment for setup, Gradle, and the Android CLI.
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PROJECT_ROOT

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Missing $1; run just setup."
}

java_is_17() {
    local version
    [[ -x "$1/bin/java" ]] || return 1
    version="$("$1/bin/java" -version 2>&1)" || return 1
    [[ "$version" == *'version "17.'* || "$version" == *'version "17"'* ]]
}

configure_android_env() {
    local candidate brew_prefix
    ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
    [[ "$ANDROID_HOME" == /* ]] || fail "ANDROID_HOME must be an absolute SDK path."
    export ANDROID_HOME
    export ANDROID_SDK_ROOT="$ANDROID_HOME"

    if java_is_17 "${JAVA_HOME:-}"; then
        candidate="$JAVA_HOME"
    elif command -v brew >/dev/null 2>&1 && brew_prefix="$(brew --prefix openjdk@17 2>/dev/null)"; then
        candidate="$brew_prefix/libexec/openjdk.jdk/Contents/Home"
    elif [[ "$(uname -s)" == Darwin ]]; then
        candidate="$(/usr/libexec/java_home -v 17 2>/dev/null)" || fail "JDK 17 required; run just setup."
    else
        fail "JDK 17 required; set JAVA_HOME to a JDK 17 installation."
    fi
    java_is_17 "$candidate" || fail "JDK 17 required; invalid Java installation: $candidate"
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
}

validate_sdk() {
    [[ -x "$ANDROID_HOME/platform-tools/adb" ]] || fail "SDK platform-tools missing; run just setup."
    [[ -f "$ANDROID_HOME/platforms/android-35/android.jar" ]] || fail "SDK platform android-35 missing; run just setup."
    [[ -x "$ANDROID_HOME/build-tools/35.0.0/aapt2" ]] || fail "SDK build-tools 35.0.0 missing; run just setup."
}
