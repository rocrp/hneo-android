#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=scripts/android-env.sh
source "$(dirname "$0")/android-env.sh"

configure_android_env
validate_sdk
require_command android
cd "$PROJECT_ROOT"
apk="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"

require_device() {
    [[ -n "${1:-}" && "$1" != -* ]] || fail "An explicit Android device serial is required; find it with just doctor."
    local state
    state="$(adb -s "$1" get-state)" || fail "Device $1 unavailable; check USB debugging and authorization."
    [[ "$state" == device ]] || fail "Device $1 is $state; check USB debugging and authorization."
}

apk_metadata() {
    local badging
    badging="$("$ANDROID_HOME/build-tools/35.0.0/aapt2" dump badging "$1")"
    apk_package="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$badging")"
    apk_activity="$(sed -n "s/^launchable-activity: name='\([^']*\)'.*/\1/p" <<< "$badging")"
    [[ -n "$apk_package" ]] || fail "Cannot read package identity from $1"
}

[[ $# -ge 1 ]] || fail "Usage: android-dev.sh doctor|gradle|run|install|screenshot|layout ..."
action="$1"
shift
case "$action" in
    doctor)
        [[ $# -eq 0 ]] || fail "Usage: android-dev.sh doctor"
        printf 'Project: %s\nSDK: %s\nJDK: %s\n' "$PROJECT_ROOT" "$ANDROID_HOME" "$JAVA_HOME"
        java -version
        android --version
        android --sdk="$ANDROID_HOME" info
        adb devices -l
        ;;
    gradle)
        [[ $# -ge 1 ]] || fail "At least one Gradle task is required."
        exec ./gradlew "$@"
        ;;
    run|install)
        [[ $# -eq 1 ]] || fail "Usage: android-dev.sh $action SERIAL"
        require_device "$1"
        ./gradlew assembleDebug
        [[ -f "$apk" ]] || fail "Build did not produce $apk"
        cli_args=("--device=$1" "--apks=$apk")
        if [[ "$action" == run ]]; then
            apk_metadata "$apk"
            [[ -n "$apk_activity" ]] || fail "No launcher activity found in $apk"
            cli_args+=("--activity=$apk_activity")
        fi
        exec android --sdk="$ANDROID_HOME" "$action" "${cli_args[@]}"
        ;;
    device-test)
        [[ $# -eq 1 ]] || fail "Usage: android-dev.sh device-test SERIAL"
        require_device "$1"
        export ANDROID_SERIAL="$1"
        test_apk="$PROJECT_ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
        ./gradlew assembleDebug assembleDebugAndroidTest
        [[ -f "$test_apk" ]] || fail "Build did not produce $test_apk"
        android --sdk="$ANDROID_HOME" install --device="$1" --apks="$apk"
        android --sdk="$ANDROID_HOME" install --device="$1" --apks="$test_apk" --install-options=-t
        apk_metadata "$apk"
        adb -s "$1" shell pm enable "$apk_package"
        apk_metadata "$test_apk"
        printf 'Enabling owned test package %s on %s.\n' "$apk_package" "$1"
        adb -s "$1" shell pm enable "$apk_package"
        enabled_packages="$(adb -s "$1" shell pm list packages -e "$apk_package")"
        [[ "$enabled_packages" == *"package:$apk_package"* ]] || fail "Test APK remains disabled; inspect: adb -s $1 shell dumpsys package $apk_package"
        adb -s "$1" shell input keyevent KEYCODE_WAKEUP
        exec ./gradlew connectedDebugAndroidTest
        ;;
    screenshot)
        [[ $# -eq 2 && -n "$2" ]] || fail "Usage: android-dev.sh screenshot SERIAL OUTPUT.png"
        require_device "$1"
        mkdir -p "$(dirname "$2")"
        exec android --sdk="$ANDROID_HOME" screen capture --device="$1" --output="$2"
        ;;
    layout)
        [[ $# -eq 1 ]] || fail "Usage: android-dev.sh layout SERIAL"
        require_device "$1"
        exec android --sdk="$ANDROID_HOME" layout --device="$1" --full --pretty
        ;;
    *) fail "Unknown action: $action" ;;
esac
