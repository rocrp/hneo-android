package dev.rocry.hneo.data

import dev.rocry.hneo.model.FeedKind
import dev.rocry.hneo.model.Story
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Last-seen stories per feed, so a cold start shows something before the network answers. */
class StoryCache(
    private val cacheDir: File,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun load(feed: FeedKind): List<Story>? = withContext(ioDispatcher) {
        val file = fileFor(feed)
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString<List<Story>>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun save(feed: FeedKind, stories: List<Story>) = withContext(ioDispatcher) {
        cacheDir.mkdirs()
        fileFor(feed).writeText(json.encodeToString(stories))
    }

    private fun fileFor(feed: FeedKind) = File(cacheDir, "${feed.name.lowercase()}.json")
}
