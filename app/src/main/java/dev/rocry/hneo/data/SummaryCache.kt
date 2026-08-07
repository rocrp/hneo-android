package dev.rocry.hneo.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedSummary(
    val text: String,
    val commentsCount: Int,
    val model: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Story summaries, kept across launches so a re-open costs nothing.
 *
 * Reads load the file on first use rather than relying on a separate `load()`
 * having already won a race — a cold open must not pay for an LLM call it has
 * already paid for.
 */
class SummaryCache(
    private val file: File,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var entries: MutableMap<String, CachedSummary>? = null

    /**
     * The cached summary for [storyId], or null when there is none worth reusing:
     * a different model, or enough new comments that the summary is stale.
     */
    suspend fun get(storyId: Int, currentCommentsCount: Int, currentModel: String): CachedSummary? {
        val cached = entries().get(storyId.toString()) ?: return null
        if (cached.model != currentModel) return null
        val newComments = currentCommentsCount - cached.commentsCount
        val tolerance = maxOf(MIN_TOLERANCE, cached.commentsCount / 10)
        return if (newComments > tolerance) null else cached
    }

    suspend fun put(storyId: Int, summary: CachedSummary) = mutex.withLock {
        val loaded = loadedLocked()
        loaded[storyId.toString()] = summary
        withContext(ioDispatcher) {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(loaded))
        }
    }

    private suspend fun entries(): Map<String, CachedSummary> = mutex.withLock { loadedLocked() }

    private suspend fun loadedLocked(): MutableMap<String, CachedSummary> {
        entries?.let { return it }
        val loaded = withContext(ioDispatcher) {
            if (!file.exists()) return@withContext mutableMapOf()
            try {
                json.decodeFromString<MutableMap<String, CachedSummary>>(file.readText())
            } catch (_: Exception) {
                mutableMapOf()
            }
        }
        entries = loaded
        return loaded
    }

    private companion object {
        const val MIN_TOLERANCE = 5
    }
}
