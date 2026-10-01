# hneo Android

Hacker News reader built with Kotlin and Jetpack Compose. Android 8.0+ (API 26); compile/target SDK 35; JDK 17.

## macOS setup

Install [Homebrew](https://brew.sh), then bootstrap the checkout:

```sh
bash scripts/setup-android.sh
just doctor
just check
```

`just setup` repeats setup after `just` is installed. Setup installs missing Homebrew packages: `just`, `openjdk@17`, `android-commandlinetools` (includes the official Android CLI bootstrap), and Android Studio. It initializes the CLI and installs SDK platform-tools, Android 35 platform/sources, and build-tools 35.0.0. First initialization may prompt for licenses or CLI settings.

SDK selection: existing `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, then `~/Library/Android/sdk`. Set `ANDROID_HOME` before setup to choose another absolute path. Setup writes ignored `local.properties`; no machine path is committed. Scripts select JDK 17 even when the shell inherits a newer Java version, without changing global Java settings. On Linux, install SDK/JDK/CLI manually and set `JAVA_HOME` and `ANDROID_HOME`; use the Gradle wrapper or development scripts. Windows users can use `gradlew.bat` directly.

Android Studio is available for editing, debugger, and Compose previews. SDK management, deployment, screenshots, and UI inspection use the [official Android CLI](https://developer.android.com/tools/agents/android-cli). Gradle remains the project build/test engine; the CLI receives its APK output.

Android Studio's Gradle JDK is separate from the IDE runtime. This project's Gradle 8.13 uses JDK 17; Studio's bundled JBR 25 is incompatible. Setup writes ignored `.gradle/config.properties` and, for a new checkout, `.idea/gradle.xml` selecting `GRADLE_LOCAL_JAVA_HOME`. Existing IDE preferences remain intact: select JDK 17 or `GRADLE_LOCAL_JAVA_HOME` under **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**. Keep Studio itself on its bundled runtime. See [Android's JDK configuration guide](https://developer.android.com/build/jdks).

## Development

```sh
just test                 # JVM unit tests
just device-test SERIAL    # Compose paging regressions on a real device
just debug                # app/build/outputs/apk/debug/app-debug.apk
just build                # app/build/outputs/apk/release/app-release.apk
just check                # tests, lint, debug + release builds
just doctor               # JDK/SDK validation, environment, device serials
just run SERIAL           # build, install, launch
just install SERIAL       # build and install
just layout SERIAL        # current UI hierarchy as JSON
just screenshot SERIAL captures/boox.png
just clean
```

Enable developer options and USB debugging on the device, connect USB, and accept the Mac's debugging authorization prompt. Copy its serial from `just doctor`; every deployment/inspection command requires an explicit serial to prevent selecting another attached device. Inspect captured PNGs visually alongside the UI hierarchy.

Deployment reads package and launcher activity from the built APK, including builds with a different application ID. `just run` supplies the activity explicitly to the CLI. Connected tests retain both APKs and app settings (`android.injected.androidTest.leaveApksInstalledAfterRun=true`).

`just device-test` builds and installs both APKs with the CLI, enables the owned app/test packages, checks the test package's enabled state, wakes the display, then starts Gradle's connected tests. Unlock a secured device before testing. This handles BOOX freezing newly installed instrumentation packages and backgrounding test activities behind its sleep screen.

For platform documentation and tool updates:

```sh
android docs search 'Compose adaptive layouts'
android update
```

Use `just` for local development. Existing `Makefile` remains available for its release naming and Telegram beta distribution workflow; `just` commands do not publish releases or messages.
The legacy Makefile and GitHub release workflow explicitly use Git commit count for APK version, filename and `build-*` tag, overriding any local `hneo.versionCode` setting to keep update comparisons consistent.

Release signing defaults to Gradle's generated debug keystore for local builds. For distribution, set all four environment variables: `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Empty values count as unset; partial configuration or a missing keystore fails immediately. Updates require the same application ID and signing key as the installed app.

## Forks and build configuration

Pass Gradle properties with `-P`, or add them to your untracked `~/.gradle/gradle.properties`:

| Property | Default / contract |
|---|---|
| `hneo.applicationId` | `dev.rocry.hneo`; change for a separately installed fork |
| `hneo.hnBaseUrl` | `https://api.hackerwebapp.com`; HTTPS service implementing the HackerWeb feed/item schema |
| `hneo.releasesUrl` | `https://api.github.com/repos/rocrp/hneo-android/releases/latest`; HTTPS GitHub latest-release endpoint |
| `hneo.versionCode` | Git commit count; positive integer override required for source archives without `.git` |

```sh
./scripts/android-dev.sh gradle assembleDebug -Phneo.applicationId=org.example.hneo -Phneo.releasesUrl=https://api.github.com/repos/example/hneo/releases/latest
```

Release tags use `build-<versionCode>` and must include an APK asset. Forks should set their own update source before distributing. Debug builds skip automatic update checks; manual checks remain available. Release builds honor the user's auto-update setting.

LLM endpoint, model, API key and prompts are editable in Settings. The client expects an HTTPS OpenAI-compatible endpoint and a nonempty API key. Sharing keeps the existing `paste.dzzu.net` service: the Share action uploads Markdown and shares the returned link. No API keys or developer credentials are shipped.

## BOOX Note X3 Pro

Connected target: Android 12 (API 32), physical portrait display 1860 × 2480 pixels, density 300 dpi (approximately 992 × 1323 dp before system bars). Landscape dimensions reverse to 2480 × 1860. Its OS meets this app's API 26 minimum; SDK 35 is the build target, not the minimum device OS.

BOOX froze freshly installed packages during validation (`enabled=3`, `lastDisabledCaller=com.onyx` in `dumpsys package`). A frozen app cannot launch; a frozen instrumentation package can fail test teardown with `ActivityNotFoundException` for `InstrumentationActivityInvoker$EmptyActivity`. Enable hneo in BOOX's app management, or run the targeted command below. CLI layout inspection similarly requires its helper package enabled. These commands affect only the named packages:

```sh
adb -s SERIAL shell pm enable dev.rocry.hneo
adb -s SERIAL shell pm enable com.android.cli.interact.instrumentation
adb -s SERIAL shell dumpsys package dev.rocry.hneo
```

BOOX can freeze new packages shortly after `pm enable`. Opening hneo's icon in the BOOX launcher also enables and launches it. Check BOOX's app freezing controls if it becomes disabled again; global freezing settings need not change.

Tablet changes: automatic E-Ink theme on ONYX/BOOX (explicit Normal/E-Ink overrides remain available); centered 760dp reading column; larger tablet typography; no E-Ink navigation fades; 48dp page controls; density-aware swipes; pixel paging with 10% overlap, including single/last comments longer than a viewport; black/white Reader CSS.

Validation details and artifact paths: [BOOX validation](docs/boox-validation.md). Screenshots verify rendered layout, not physical panel ghosting or refresh quality. AI requests require an API key and were not exercised on this fresh installation.
