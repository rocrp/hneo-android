package dev.rocry.hneo.data

import dev.rocry.hneo.data.http.HttpRequest
import dev.rocry.hneo.data.http.JsonHttp
import dev.rocry.hneo.data.http.decode
import dev.rocry.hneo.model.FeedKind
import dev.rocry.hneo.model.Story
import dev.rocry.hneo.model.StoryDetail

/** Reads Hacker News. Failures arrive as [dev.rocry.hneo.data.http.HttpFailure]. */
class HNClient(private val http: JsonHttp) {

    suspend fun fetchStories(feed: FeedKind, page: Int = 1): List<Story> =
        http.decode(HttpRequest("$BASE_URL/${feed.endpoint}?page=$page"))

    suspend fun fetchStoryDetail(id: Int): StoryDetail =
        http.decode(HttpRequest("$BASE_URL/item/$id"))

    private companion object {
        const val BASE_URL = "https://api.hackerwebapp.com"
    }
}
