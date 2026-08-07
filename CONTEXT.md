# CONTEXT — hneo-android domain glossary

Vocabulary for tickets, code, and reviews. Use these names exactly.

- **LLM Document** — a streamed LLM response rendered as a screen. Three request shapes: story summary, page summary, explanation. One module serves all three; per-shape screens are forbidden (they drift).
- **Story Repository** — the single home for story data: story + comment tree, composing the HN client and caches. Keyed by story id; navigation passes ids, never serialized objects.
- **E-Ink Reading Surface** — the module that renders content as discrete pages in e-ink mode. Owns jump/overlap arithmetic, volume-key transport, and page chrome (prev/next/indicator). Adapters: list content, continuous text.
- **Composition Root** — the one place (app start) where modules are constructed and wired. Everything else receives dependencies; nothing constructs its own.
- **AppUpdater** — APK self-update policy + mechanism as one state machine (check → download → install). UI surfaces are thin adapters over it.
- **Reader** — reader-mode inside the web view: content selectors, stylesheet, font resolution. Shares one font table with the app's font pipeline.
- **Page Text Extractor** — pulls plain text out of a web page (JS payload + full unescape) to feed a page-summary LLM Document.
- **Chrome** — bars, buttons, and navigation furniture around content, as opposed to the content surface itself.

ADRs: none yet — record load-bearing rejections in `docs/adr/` when they happen.
