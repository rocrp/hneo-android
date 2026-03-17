package dev.rocry.hneo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object OpenGraphService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cache = ConcurrentHashMap<String, String?>()
    private val ogPattern = Regex(
        """<meta[^>]*property\s*=\s*["']og:image["'][^>]*content\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val ogPatternReverse = Regex(
        """<meta[^>]*content\s*=\s*["']([^"']+)["'][^>]*property\s*=\s*["']og:image["']""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun fetchOgImage(url: String): String? {
        cache[url]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()?.take(50_000) ?: return@withContext null
                val imageUrl = ogPattern.find(body)?.groupValues?.get(1)
                    ?: ogPatternReverse.find(body)?.groupValues?.get(1)
                    ?: return@withContext null

                val resolved = when {
                    imageUrl.startsWith("http") -> imageUrl
                    imageUrl.startsWith("//") -> "https:$imageUrl"
                    imageUrl.startsWith("/") -> {
                        val base = url.substringBefore("/", url).let {
                            val scheme = url.substringBefore("://")
                            val host = url.removePrefix("$scheme://").substringBefore("/")
                            "$scheme://$host"
                        }
                        "$base$imageUrl"
                    }
                    else -> imageUrl
                }
                cache[url] = resolved
                resolved
            } catch (_: Exception) {
                cache[url] = null
                null
            }
        }
    }
}
