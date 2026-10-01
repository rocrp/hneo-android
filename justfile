set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

# Show available development commands.
default:
    @just --list

# Install missing macOS tools and configure this checkout's SDK.
setup:
    ./scripts/setup-android.sh

# Validate JDK/SDK and list connected Android devices.
doctor:
    ./scripts/android-dev.sh doctor

# Run JVM unit tests.
test:
    ./scripts/android-dev.sh gradle testDebugUnitTest

# Run Compose regression tests on the explicitly selected device.
device-test serial:
    ./scripts/android-dev.sh device-test {{ quote(serial) }}

# Build the signed, optimized release APK.
build:
    ./scripts/android-dev.sh gradle assembleRelease

# Build the development APK.
debug:
    ./scripts/android-dev.sh gradle assembleDebug

# Unit tests, Android lint, and both APK variants.
check:
    ./scripts/android-dev.sh gradle testDebugUnitTest lintDebug assembleDebug assembleRelease

# Build, install, and launch on the explicitly selected device.
run serial:
    ./scripts/android-dev.sh run {{ quote(serial) }}

# Build and install on the explicitly selected device.
install serial:
    ./scripts/android-dev.sh install {{ quote(serial) }}

# Save a device screenshot to a PNG path.
screenshot serial output:
    ./scripts/android-dev.sh screenshot {{ quote(serial) }} {{ quote(output) }}

# Print the device's current UI hierarchy as JSON.
layout serial:
    ./scripts/android-dev.sh layout {{ quote(serial) }}

clean:
    ./scripts/android-dev.sh gradle clean
