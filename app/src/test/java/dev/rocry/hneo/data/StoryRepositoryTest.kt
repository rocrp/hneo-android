package dev.rocry.hneo.data

import dev.rocry.hneo.data.http.FakeHttpEngine
import dev.rocry.hneo.data.http.HttpFailure
import dev.rocry.hneo.data.http.JsonHttp
import dev.rocry.hneo.model.FeedKind
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoryRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }
    private val dispatcher = UnconfinedTestDispatcher()
    private val engine = FakeHttpEngine()

    private val repository by lazy {
        StoryRepository(
            hnClient = HNClient(JsonHttp(engine, json, dispatcher)),
            storyCache = StoryCache(tempFolder.root, json, dispatcher),
            commentCache = CommentCache(),
        )
    }

    private val feedBody = """
        [{"id":1,"title":"First","comments_count":2},
         {"id":2,"title":"Second","comments_count":0}]
    """.trimIndent()

    private val detailBody = """
        {"id":1,"title":"First","points":30,"comments_count":2,"comments":[
          {"id":10,"user":"alice","content":"hello","comments":[
            {"id":11,"user":"bob","content":"hi back","comments":[]}]}]}
    """.trimIndent()

    @Test
    fun `fetching a feed returns stories and remembers them by id`() = runTest {
        engine.respond(body = feedBody)

        val stories = repository.fetchStories(FeedKind.TOP)

        assertEquals(listOf("First", "Second"), stories.map { it.title })
        assertEquals("First", repository.knownStory(1)?.title)
    }

    @Test
    fun `a cached feed survives without the network`() = runTest {
        engine.respond(body = feedBody)
        repository.cacheStories(FeedKind.TOP, repository.fetchStories(FeedKind.TOP))

        engine.failWith(java.io.IOException("offline"))

        assertEquals(listOf("First", "Second"), repository.cachedStories(FeedKind.TOP)?.map { it.title })
    }

    @Test
    fun `an empty feed cache is a miss, not an error`() = runTest {
        assertNull(repository.cachedStories(FeedKind.BEST))
    }

    @Test
    fun `fetching detail caches the comment tree`() = runTest {
        engine.respond(body = detailBody)

        repository.fetchDetail(1)

        val cached = repository.cachedDetail(1)
        assertEquals(1, cached?.comments?.size)
        assertEquals("bob", cached?.comments?.get(0)?.comments?.get(0)?.user)
    }

    @Test
    fun `detail serves the cache on a hit and never touches the network`() = runTest {
        engine.respond(body = detailBody)
        repository.fetchDetail(1)
        val requestsAfterFetch = engine.requests.size

        val again = repository.detail(1)

        assertEquals(1, again.id)
        assertEquals(requestsAfterFetch, engine.requests.size)
    }

    @Test
    fun `detail fetches on a cache miss`() = runTest {
        engine.respond(body = detailBody)

        val detail = repository.detail(1)

        assertEquals(30, detail.points)
        assertEquals(1, engine.requests.size)
        assertEquals("https://api.hackerwebapp.com/item/1", engine.lastRequest.url)
    }

    @Test
    fun `a detail fetch failure surfaces to the caller`() = runTest {
        engine.respond(code = 500, body = "server on fire")

        assertFailsWith<HttpFailure.Status> { repository.detail(1) }
    }

    @Test
    fun `a story seen only as detail is still known by id`() = runTest {
        engine.respond(body = detailBody)
        repository.fetchDetail(1)

        assertEquals("First", repository.knownStory(1)?.title)
        assertNull(repository.knownStory(999))
    }
}
