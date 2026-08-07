package dev.rocry.hneo.data.llm

import dev.rocry.hneo.data.AppSettings
import dev.rocry.hneo.data.FakeSettingsStore
import dev.rocry.hneo.data.assertFailsWith
import dev.rocry.hneo.data.http.FakeHttpEngine
import dev.rocry.hneo.data.http.HttpFailure
import dev.rocry.hneo.data.http.HttpMethod
import dev.rocry.hneo.model.FlatComment
import dev.rocry.hneo.model.Story
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OpenAiLlmClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val engine = FakeHttpEngine()
    private val settingsStore = FakeSettingsStore(AppSettings(llmApiKey = "sk-test", llmModel = "test-model"))
    private val client = OpenAiLlmClient(engine, json, settingsStore, UnconfinedTestDispatcher())

    private val explain = LlmRequest.Explain(selectedText = "monads", storyTitle = "FP thread")

    private fun sse(vararg contents: String) =
        contents.joinToString("\n") { """data: {"choices":[{"delta":{"content":"$it"}}]}""" } +
            "\ndata: [DONE]\n"

    private suspend fun textOf(request: LlmRequest) =
        client.stream(request).toList().filterIsInstance<LlmEvent.Chunk>().joinToString("") { it.text }

    @Test
    fun `streams the model name before the first chunk`() = runTest {
        engine.respond(body = sse("hi"))

        val events = client.stream(explain).toList()

        assertEquals(LlmEvent.Started("test-model"), events.first())
        assertEquals("hi", textOf(explain))
    }

    @Test
    fun `a blank API key fails before any request is made`() = runTest {
        settingsStore.update { it.copy(llmApiKey = "  ") }

        assertFailsWith<LlmFailure.MissingApiKey> { client.stream(explain).toList() }

        assertTrue("no request should have been sent", engine.requests.isEmpty())
    }

    @Test
    fun `non-2xx surfaces the API error body, not just the status`() = runTest {
        engine.respond(code = 400, body = """{"error":{"message":"model not found"}}""")

        val failure = assertFailsWith<HttpFailure.Status> { client.stream(explain).toList() }

        assertEquals(400, failure.code)
        assertTrue(failure.message!!.contains("model not found"))
    }

    @Test
    fun `the response is closed on the success path`() = runTest {
        engine.respond(body = sse("done"))

        client.stream(explain).toList()

        assertTrue(engine.responses.single().closed)
    }

    @Test
    fun `the response is closed on the failure path`() = runTest {
        engine.respond(code = 500, body = "boom")

        runCatching { client.stream(explain).toList() }

        assertTrue(engine.responses.single().closed)
    }

    @Test
    fun `transport failures propagate`() = runTest {
        engine.failWith(IOException("connection reset"))

        assertFailsWith<IOException> { client.stream(explain).toList() }
    }

    @Test
    fun `the request carries auth, the configured endpoint and stream mode`() = runTest {
        settingsStore.update { it.copy(llmApiUrl = "https://llm.example/v1/chat/completions") }
        engine.respond(body = sse("x"))

        client.stream(explain).toList()

        val sent = engine.lastRequest
        assertEquals("https://llm.example/v1/chat/completions", sent.url)
        assertEquals(HttpMethod.POST, sent.method)
        assertEquals("Bearer sk-test", sent.headers["Authorization"])

        val payload = json.parseToJsonElement(sent.body!!.content).jsonObject
        assertEquals("test-model", payload["model"]!!.jsonPrimitive.content)
        assertTrue(payload["stream"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `each request shape picks its own system prompt`() = runTest {
        settingsStore.update {
            it.copy(
                llmSystemPrompt = "STORY",
                llmWebpageSummaryPrompt = "PAGE",
                llmExplainPrompt = "EXPLAIN",
            )
        }
        val story = Story(id = 1, title = "T")

        val prompts = listOf(
            LlmRequest.SummarizeStory(story, emptyList()),
            LlmRequest.SummarizePage("t", "u", "c"),
            explain,
        ).map { request ->
            engine.respond(body = sse("x"))
            client.stream(request).toList()
            json.parseToJsonElement(engine.lastRequest.body!!.content)
                .jsonObject["messages"]!!.jsonArray[0]
                .jsonObject["content"]!!.jsonPrimitive.content
        }

        assertEquals(listOf("STORY", "PAGE", "EXPLAIN"), prompts)
    }

    @Test
    fun `a story summary prompt carries the comment tree`() = runTest {
        val story = Story(id = 9, title = "Rust wins", points = 42, commentsCount = 2)
        val comments = listOf(
            FlatComment(1, "alice", "1h", "I agree", depth = 0, childCount = 1),
            FlatComment(2, "bob", "30m", "I do not", depth = 1, childCount = 0),
        )
        engine.respond(body = sse("x"))

        client.stream(LlmRequest.SummarizeStory(story, comments)).toList()

        val userPrompt = json.parseToJsonElement(engine.lastRequest.body!!.content)
            .jsonObject["messages"]!!.jsonArray[1]
            .jsonObject["content"]!!.jsonPrimitive.content

        assertTrue(userPrompt.contains("[Story] Rust wins"))
        assertTrue(userPrompt.contains("[alice] I agree"))
        assertTrue(userPrompt.contains("  [bob] I do not"))
    }

    @Test
    fun `a story summary prompt honours the comment limit`() = runTest {
        settingsStore.update { it.copy(llmMaxComments = 1) }
        val comments = (1..5).map { FlatComment(it, "u$it", "1h", "body$it", 0, 0) }
        engine.respond(body = sse("x"))

        client.stream(LlmRequest.SummarizeStory(Story(id = 1, title = "T"), comments)).toList()

        val userPrompt = json.parseToJsonElement(engine.lastRequest.body!!.content)
            .jsonObject["messages"]!!.jsonArray[1]
            .jsonObject["content"]!!.jsonPrimitive.content

        assertTrue(userPrompt.contains("body1"))
        assertTrue(userPrompt.contains("body2").not())
    }
}
