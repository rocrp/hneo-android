package dev.rocry.hneo.data

/**
 * Text extracted from web pages, keyed by URL.
 *
 * Page content is far too large for a navigation route, so it is handed over
 * here instead. Keying by URL — rather than holding "the last page" — is what
 * stops two pages summarized in quick succession from racing, and lets a
 * lookup miss be reported as a miss instead of summarizing an empty string.
 */
class PageTextCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    private val entries = object : LinkedHashMap<String, String>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun put(url: String, text: String) {
        entries[url] = text
    }

    @Synchronized
    fun get(url: String): String? = entries[url]

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 4
    }
}
