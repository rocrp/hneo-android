# BOOX Note X3 Pro validation — 2026-10-01

## Environment

- Mac arm64; Homebrew JDK 17.0.20.1; Gradle wrapper 8.13; AGP 8.13.0.
- Official Android CLI 1.0.16457483 (`android update` confirmed current); `android init` installed official agent skill.
- SDK `~/w/Android/sdk`: platform/sources Android 35; build-tools 35.0.0; platform-tools 37.0.1. Installation verified with `android sdk install` / `android sdk list`.
- Android Studio 2026.1.4.8 installed. Its JBR 25 remains the IDE runtime; ignored project files select JDK 17 for Gradle. IDE sync/previews not exercised.
- `just setup` and `just doctor` passed. New checkout setup supported by repository scripts; untouched fresh Mac not tested.

## Target

- ADB identity: manufacturer ONYX; model/product/device NoteX3Pro; Android 12, API 32; authorized USB connection.
- Physical display 1860×2480, density 300 dpi. Portrait approx. 992×1323 dp before system bars; landscape 2480×1860.
- Wi-Fi initially disabled. User connected Wi-Fi; connectivity became validated and live HN stories/comments/webpage loaded.
- Rotation temporarily forced landscape for inspection; original `user_rotation=0`, `accelerometer_rotation=0` restored.

## Passed

| Check | Evidence |
|---|---|
| JVM tests | 105 tests, zero failures/errors after configuration review; `app/build/test-results/testDebugUnitTest/`. Earlier count 113 was incorrect; initial suite had 103, this review adds 2. |
| Lint + Debug/Release build | `just check`; `app/build/reports/lint-results-debug.html`; both APKs produced. Lint warnings remain. |
| Real-device paging regressions | 3 tests, zero failures/errors: single long item, final long item with all items initially visible, repeated swipes after recomposition. `just device-test` passed repeatedly. |
| Test APK retention | Final connected run preserved both installed packages. AGP option `android.injected.androidTest.leaveApksInstalledAfterRun=true`; avoids default post-test app/settings removal. |
| Launch | `just run` uses CLI with explicit MainActivity. Final `dumpsys activity` reports resumed `dev.rocry.hneo/.MainActivity`; process exists. |
| Portrait stories | Live HN feed; black/white, centered capped column, larger titles, visible Prev/Next. |
| Page button | Next changed visible range from `1–17 / 30` to `14–30 / 60`; overlap retained and more stories loaded. |
| Landscape + return | Column width remains capped; first visible item/offset retained. Returned portrait successfully. |
| Settings | Automatic selected; readable fields and font choices inside capped column. API key empty. |
| Comments | Live CHOMPI discussion displayed; enlarged selectable body text and nested indentation. |
| Web Reader | Live CHOMPI webpage loaded; Reader conversion renders black/white. Page has little article text, so this checks CSS/launch rather than extraction quality across websites. |
| Script quality | ShellCheck, Bash syntax, Just formatting, `git diff --check`, generated IDE XML syntax passed. |

Artifacts (ignored, local):

- `captures/boox-portrait.png`, `captures/boox-landscape.png`, `captures/boox-settings.png`, `captures/boox-comments.png`, `captures/boox-reader.png` — visually inspected.
- `captures/boox-portrait-layout.json`, `boox-next-layout.json`, `boox-landscape-layout.json`, `boox-settings-layout.json`, `boox-comments-layout.json`, `boox-final-layout.json` — official CLI hierarchy evidence.
- Device report: `app/build/reports/androidTests/connected/debug/index.html`.
- Installed development APK: `app/build/outputs/apk/debug/app-debug.apk`, version 0.1.38. Optimized release APK built at `app/build/outputs/apk/release/app-release.apk`; release device runtime not tested.

## Failures resolved / acceptance limits

- Wrapper's 10s initial download timed out; raised network timeout to 60s. JDK 27 failed configuration; project scripts and local IDE selection use JDK 17.
- Homebrew Studio download stalled; resumed identical official Google artifact from `dl.google.com`; Homebrew checksum/install succeeded.
- CLI `run --apks` alone installed without launching; explicit activity fixed command intent.
- BOOX froze newly installed hneo and CLI helper. Evidence: package `enabled=3`, `lastDisabledCaller=com.onyx`; launch reported missing activity, layout server lacked broadcast info. Targeted package enable restored both; launching hneo's BOOX icon also unfreezes it.
- Initial connected tests passed content assertions but failed ActivityScenario teardown: BOOX froze `dev.rocry.hneo.test`, hiding EmptyActivity. Recipe preinstalls/enables owned app/test packages; repeated complete tests passed. No global freeze setting changed.
- Later review run failed with `No compose hierarchies found`: tablet was Dozing behind `Sys2043:dream`; test activities resumed then immediately stopped. Device-test recipe now sends `KEYCODE_WAKEUP` before instrumentation; rerun passed all 3 tests in 13s.
- Android CLI delta helper emitted a device-side ClassNotFoundException during initial setup and fell back to standard install. Later delta reinstall succeeded (89KB patch vs 17.9MB APK); do not attribute this helper crash to hneo.
- AI requests not tested: fresh device has no API key. LLM Document arithmetic/cancellation covered by JVM tests; real streaming/provider behavior remains unverified.
- Digital captures verify layout/content. Physical panel ghosting, flash frequency, touch-to-visible latency, pen/text-selection feel need user observation. No vendor refresh SDK or hidden device API introduced.

## Configuration review

- Sharing remains unchanged: Markdown uploads to the existing paste service, then shares its link.
- SDK/JDK machine paths stay ignored. Legacy Makefile now uses shared environment selection and requires explicit device serial.
- Gradle properties support fork application ID, HackerWeb-compatible API base, GitHub release endpoint, and explicit version code. Debug builds skip automatic update checks.
- Custom Debug build verified APK ID `org.example.hneo`, version code 1000, configured API/release URLs, and launcher activity discovered from APK metadata. This sample fork was not installed.
- Invalid version code, HTTP API URL, partial signing configuration, missing keystore all rejected during Gradle configuration. Empty signing environment accepted; local release uses generated debug signing.
- `make build` passed with explicit Git version 38; release filename and tag metadata remain consistent with that APK. No beta message sent.
