# hneo Android

- Read `CONTEXT.md` for domain boundaries. Preserve the shared E-Ink Reading Surface and LLM Document modules.
- Prefer the official Android CLI. Read installed `android-cli/SKILL.md` and its `references/interact.md` before device inspection; use `android layout` and `android screen`. ADB remains necessary for input, device properties, logs and BOOX package freezing.
- Use `just setup`, `just doctor`, `just check`; scripts select JDK 17. Shell Java and Studio's bundled JBR can be too new for Gradle 8.13.
- Device commands require an explicit serial. Use `just run SERIAL`, `just device-test SERIAL`, `just layout SERIAL`, `just screenshot SERIAL OUTPUT`.
- Deployment must derive package/activity identities from APK metadata. Keep machine SDK/JDK paths in ignored local configuration. Fork IDs/API/update sources use documented Gradle properties; retain the existing paste-link sharing behavior.
- Layout breakpoints use current window dp, never physical pixels. Preserve pure black/white E-Ink rendering and immediate page turns. Long single/last comments must remain readable.
- Connected tests retain APKs/settings. BOOX can disable newly installed packages (`enabled=3`, `lastDisabledCaller=com.onyx`); fix only this project's app/test or CLI helper, never global freezing settings.
- Validate code with `just check`, paging changes with `just device-test SERIAL`, and layout changes with visually inspected device captures. Screenshots do not establish physical ghosting/refresh acceptance.
