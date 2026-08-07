package dev.rocry.hneo.data

import dev.rocry.hneo.data.http.HttpRequest
import dev.rocry.hneo.data.http.JsonHttp
import java.util.concurrent.ConcurrentHashMap

/** Resolves a page's `og:image` for story thumbnails. Best-effort: failures are cached as "none". */
class OpenGraphService(private val http: JsonHttp) {
    private val cache = ConcurrentHashMap<String, String>()
    private val failedUrls = ConcurrentHashMap.newKeySet<String>()

    suspend fun fetchOgImage(url: String): String? {
        cache[url]?.let { return it }
        if (url in failedUrls) return null

        val body = try {
            http.text(HttpRequest(url, readTimeoutSeconds = TIMEOUT_SECONDS)).take(MAX_BYTES)
        } catch (_: Exception) {
            failedUrls += url
            return null
        }

        val imageUrl = OG_IMAGE.find(body)?.groupValues?.get(1)
            ?: OG_IMAGE_REVERSED.find(body)?.groupValues?.get(1)
            ?: run {
                failedUrls += url
                return null
            }

        return resolveAgainst(pageUrl = url, imageUrl = imageUrl).also { cache[url] = it }
    }

    private fun resolveAgainst(pageUrl: String, imageUrl: String): String = when {
        imageUrl.startsWith("http") -> imageUrl
        imageUrl.startsWith("//") -> "https:$imageUrl"
        imageUrl.startsWith("/") -> {
            val scheme = pageUrl.substringBefore("://")
            val host = pageUrl.removePrefix("$scheme://").substringBefore("/")
            "$scheme://$host$imageUrl"
        }
        else -> imageUrl
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val MAX_BYTES = 50_000

        val OG_IMAGE = Regex(
            """<meta[^>]*property\s*=\s*["']og:image["'][^>]*content\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
        val OG_IMAGE_REVERSED = Regex(
            """<meta[^>]*content\s*=\s*["']([^"']+)["'][^>]*property\s*=\s*["']og:image["']""",
            RegexOption.IGNORE_CASE,
        )
    }
}
