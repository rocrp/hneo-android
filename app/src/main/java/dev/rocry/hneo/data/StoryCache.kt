package dev.rocry.hneo.data

import android.content.Context
import dev.rocry.hneo.model.FeedKind
import dev.rocry.hneo.model.Story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class StoryCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "stories").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(feed: FeedKind): List<Story>? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "${feed.name.lowercase()}.json")
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString<List<Story>>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun save(feed: FeedKind, stories: List<Story>) = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "${feed.name.lowercase()}.json")
        file.writeText(json.encodeToString(stories))
    }
}
