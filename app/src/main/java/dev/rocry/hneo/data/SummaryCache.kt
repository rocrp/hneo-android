package dev.rocry.hneo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CachedSummary(
    val text: String,
    val commentsCount: Int,
    val model: String,
    val timestamp: Long = System.currentTimeMillis(),
)

class SummaryCache(context: Context) {
    private val file = java.io.File(context.cacheDir, "summaries.json")
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: MutableMap<String, CachedSummary> = mutableMapOf()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (file.exists()) {
            try {
                cache = json.decodeFromString<MutableMap<String, CachedSummary>>(file.readText())
            } catch (_: Exception) {
                cache = mutableMapOf()
            }
        }
    }

    fun get(storyId: Int, currentCommentsCount: Int, currentModel: String): CachedSummary? {
        val cached = cache[storyId.toString()] ?: return null
        if (cached.model != currentModel) return null
        val delta = currentCommentsCount - cached.commentsCount
        val threshold = maxOf(5, cached.commentsCount / 10)
        if (delta > threshold) return null
        return cached
    }

    suspend fun put(storyId: Int, summary: CachedSummary) {
        cache[storyId.toString()] = summary
        withContext(Dispatchers.IO) {
            file.writeText(json.encodeToString(cache))
        }
    }
}
