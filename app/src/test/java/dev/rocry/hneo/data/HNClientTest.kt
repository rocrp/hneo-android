package dev.rocry.hneo.data

import dev.rocry.hneo.data.http.FakeHttpEngine
import dev.rocry.hneo.data.http.HttpFailure
import dev.rocry.hneo.data.http.JsonHttp
import dev.rocry.hneo.model.FeedKind
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HNClientTest {
    private val engine = FakeHttpEngine()
    private val client = HNClient(
        JsonHttp(engine, Json { ignoreUnknownKeys = true }, UnconfinedTestDispatcher()),
    )

    @Test
    fun `fetchStories decodes the feed`() = runTest {
        engine.respond(
            body = """
                [{"id":1,"title":"First","url":"https://a.example","points":10,
                  "comments_count":3,"time_ago":"1 hour ago","domain":"a.example"}]
            """.trimIndent(),
        )

        val stories = client.fetchStories(FeedKind.TOP, page = 2)

        assertEquals(1, stories.size)
        assertEquals("First", stories[0].title)
        assertEquals(3, stories[0].commentsCount)
        assertEquals("https://api.hackerwebapp.com/news?page=2", engine.lastRequest.url)
    }

    @Test
    fun `fetchStoryDetail decodes nested comments`() = runTest {
        engine.respond(
            body = """
                {"id":42,"title":"Deep","comments":[
                  {"id":7,"user":"alice","content":"top","comments":[
                    {"id":8,"user":"bob","content":"reply","comments":[]}]}]}
            """.trimIndent(),
        )

        val detail = client.fetchStoryDetail(42)

        assertEquals(42, detail.id)
        assertEquals(1, detail.comments.size)
        assertEquals("bob", detail.comments[0].comments[0].user)
        assertEquals("https://api.hackerwebapp.com/item/42", engine.lastRequest.url)
    }

    @Test
    fun `non-2xx surfaces as a status failure carrying the body`() = runTest {
        engine.respond(code = 503, body = "upstream is down")

        val failure = assertFailsWith<HttpFailure.Status> { client.fetchStories(FeedKind.TOP) }

        assertEquals(503, failure.code)
        assertEquals("upstream is down", failure.body)
        assertTrue(failure.message!!.contains("upstream is down"))
    }

    @Test
    fun `malformed 2xx body surfaces as a malformed failure, not a parser message`() = runTest {
        engine.respond(body = "<html>not json</html>")

        val failure = assertFailsWith<HttpFailure.Malformed> { client.fetchStories(FeedKind.TOP) }

        assertEquals("Unexpected response format from the server", failure.message)
    }

    @Test
    fun `unreachable server surfaces as a transport failure`() = runTest {
        engine.failWith(IOException("no route to host"))

        val failure = assertFailsWith<HttpFailure.Transport> { client.fetchStories(FeedKind.TOP) }

        assertEquals("no route to host", failure.message)
    }

    @Test
    fun `the three failures are distinguishable from each other`() = runTest {
        engine.respond(code = 404, body = "nope")
        val status: HttpFailure = assertFailsWith<HttpFailure.Status> { client.fetchStories(FeedKind.TOP) }

        engine.respond(body = "{oops")
        val malformed: HttpFailure = assertFailsWith<HttpFailure.Malformed> { client.fetchStories(FeedKind.TOP) }

        assertTrue(status !is HttpFailure.Malformed)
        assertTrue(malformed !is HttpFailure.Status)
    }
}

internal inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("Expected ${T::class.simpleName} but got ${e::class.simpleName}: ${e.message}", e)
    }
    throw AssertionError("Expected ${T::class.simpleName} but nothing was thrown")
}
