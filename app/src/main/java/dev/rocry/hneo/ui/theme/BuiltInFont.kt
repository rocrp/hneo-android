package dev.rocry.hneo.ui.theme

/**
 * The fonts that ship with the app. One table — the picker, the font manager and
 * the reader stylesheet all read these names from here, so they cannot drift apart.
 */
enum class BuiltInFont(val displayName: String, val cssFamily: String) {
    SYSTEM("System", "system-ui,-apple-system,Roboto,sans-serif"),
    SERIF("Serif", "Georgia,serif"),
    MONOSPACE("Monospace", "'Courier New',Courier,monospace");

    companion object {
        /** A blank choice means "whatever the system uses". */
        fun forChoice(fontChoice: String): BuiltInFont? =
            if (fontChoice.isBlank()) SYSTEM else entries.find { it.displayName == fontChoice }
    }
}
