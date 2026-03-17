package dev.rocry.hneo.data

import dev.rocry.hneo.model.FeedKind
import dev.rocry.hneo.model.Story
import dev.rocry.hneo.model.StoryDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object HNClient {
    private const val BASE_URL = "https://api.hackerwebapp.com"

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchStories(feed: FeedKind, page: Int = 1): List<Story> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/${feed.endpoint}?page=$page"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        json.decodeFromString<List<Story>>(body)
    }

    suspend fun fetchStoryDetail(id: Int): StoryDetail = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/item/$id"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        json.decodeFromString<StoryDetail>(body)
    }
}
