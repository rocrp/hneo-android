package dev.rocry.hneo.ui.llmdocument

import dev.rocry.hneo.MainDispatcherRule
import dev.rocry.hneo.data.CachedSummary
import dev.rocry.hneo.data.CommentCache
import dev.rocry.hneo.data.HNClient
import dev.rocry.hneo.data.PageTextCache
import dev.rocry.hneo.data.StoryCache
import dev.rocry.hneo.data.StoryRepository
import dev.rocry.hneo.data.SummaryCache
import dev.rocry.hneo.data.http.FakeHttpEngine
import dev.rocry.hneo.data.http.JsonHttp
import dev.rocry.hneo.data.llm.FakeLlmClient
import dev.rocry.hneo.data.llm.LlmRequest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LlmDocumentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }
    private val dispatcher = UnconfinedTestDispatcher()
    private val llmClient = FakeLlmClient()
    private val pageTextCache = PageTextCache()
    private val engine = FakeHttpEngine()

    private val repository by lazy {
        StoryRepository(
            hnClient = HNClient(JsonHttp(engine, json, dispatcher)),
            storyCache = StoryCache(File(tempFolder.root, "stories"), json, dispatcher),
            commentCache = CommentCache(),
        )
    }

    private val storyId = 7

    /** A story with a real discussion under it. */
    private val storyWithComments = """
        {"id":7,"title":"Story","points":12,"comments_count":3,"comments":[
          {"id":10,"user":"alice","content":"first take","comments":[
            {"id":11,"user":"bob","content":"counterpoint","comments":[]}]},
          {"id":12,"user":"carol","content":"third","comments":[]}]}
    """.trimIndent()

    private fun summaryCacheWith(vararg entries: Pair<Int, CachedSummary>): SummaryCache {
        val file = File(tempFolder.root, "summaries.json")
        if (entries.isNotEmpty()) {
            file.writeText(json.encodeToString(entries.associate { it.first.toString() to it.second }))
        }
        return SummaryCache(file, json, dispatcher)
    }

    private fun viewModel(summaryCache: SummaryCache = summaryCacheWith()) =
        LlmDocumentViewModel(llmClient, repository, summaryCache, pageTextCache)

    private fun summarizeRequest() = llmClient.lastRequest as LlmRequest.SummarizeStory

    @Test
    fun `a story summary is generated from the story's real comments`() = runTest {
        // The headline defect: the summary destination used to build a fresh,
        // never-initialised comment ViewModel, so every prompt had zero comments.
        engine.respond(body = storyWithComments)

        viewModel().summarizeStory(storyId)

        val comments = summarizeRequest().comments
        assertEquals(
            listOf("first take", "counterpoint", "third"),
            comments.map { it.text },
        )
        assertEquals(listOf("alice", "bob", "carol"), comments.map { it.user })
    }

    @Test
    fun `a summary reuses the detail the comments screen already loaded`() = runTest {
        engine.respond(body = storyWithComments)
        repository.fetchDetail(storyId)
        val requestsAfterComments = engine.requests.size

        viewModel().summarizeStory(storyId)

        assertEquals("no second fetch", requestsAfterComments, engine.requests.size)
        assertEquals(3, summarizeRequest().comments.size)
    }

    @Test
    fun `the summarized story carries its own metadata`() = runTest {
        engine.respond(body = storyWithComments)

        viewModel().summarizeStory(storyId)

        val story = summarizeRequest().story
        assertEquals("Story", story.title)
        assertEquals(12, story.points)
        assertEquals(3, story.commentsCount)
    }

    @Test
    fun `a story that cannot be loaded is reported instead of summarized`() = runTest {
        engine.respond(code = 500, body = "server on fire")
        val vm = viewModel()

        vm.summarizeStory(storyId)

        assertTrue(vm.state.value.error!!.contains("500"))
        assertTrue("no LLM call should have been made", llmClient.requests.isEmpty())
    }

    @Test
    fun `a cold open of a previously summarized story serves the cache`() = runTest {
        // The load-before-get race used to make this miss and re-bill the user.
        engine.respond(body = storyWithComments)
        val cache = summaryCacheWith(
            storyId to CachedSummary(text = "cached answer", commentsCount = 3, model = "fake-model"),
        )

        val vm = viewModel(cache)
        vm.summarizeStory(storyId)

        assertEquals("cached answer", vm.state.value.text)
        assertTrue(vm.state.value.isCached)
        assertTrue("no LLM call should have been made", llmClient.requests.isEmpty())
    }

    @Test
    fun `a summary cached under a different model is not reused`() = runTest {
        engine.respond(body = storyWithComments)
        val cache = summaryCacheWith(
            storyId to CachedSummary(text = "old", commentsCount = 3, model = "some-other-model"),
        )

        val vm = viewModel(cache)
        vm.summarizeStory(storyId)

        assertEquals("an answer", vm.state.value.text)
        assertFalse(vm.state.value.isCached)
    }

    @Test
    fun `a fresh summary is written to the cache and served on the next open`() = runTest {
        engine.respond(body = storyWithComments)

        viewModel(summaryCacheWith()).summarizeStory(storyId)
        llmClient.requests.clear()

        // A brand-new cache over the same file — this is the cold-open path.
        val reopened = viewModel(summaryCacheWith())
        reopened.summarizeStory(storyId)

        assertEquals("an answer", reopened.state.value.text)
        assertTrue(reopened.state.value.isCached)
        assertTrue(llmClient.requests.isEmpty())
    }

    @Test
    fun `restarting with the same story does not re-bill`() = runTest {
        engine.respond(body = storyWithComments)
        val vm = viewModel()

        vm.summarizeStory(storyId)
        vm.summarizeStory(storyId)

        assertEquals(1, llmClient.requests.size)
    }

    @Test
    fun `refresh re-runs the request even when it was served from cache`() = runTest {
        engine.respond(body = storyWithComments)
        val cache = summaryCacheWith(
            storyId to CachedSummary(text = "cached", commentsCount = 3, model = "fake-model"),
        )
        val vm = viewModel(cache)
        vm.summarizeStory(storyId)

        llmClient.reply = "regenerated"
        vm.refresh()

        assertEquals("regenerated", vm.state.value.text)
        assertFalse(vm.state.value.isCached)
        assertEquals(1, llmClient.requests.size)
    }

    @Test
    fun `a page summary reads its content out of the page text cache`() = runTest {
        pageTextCache.put("https://a.example", "the page body")

        viewModel().summarizePage("A Page", "https://a.example")

        val request = llmClient.lastRequest as LlmRequest.SummarizePage
        assertEquals("the page body", request.content)
        assertEquals("A Page", request.title)
    }

    @Test
    fun `a missing page text is reported, never summarized as an empty string`() = runTest {
        val vm = viewModel()

        vm.summarizePage("A Page", "https://gone.example")

        assertTrue(vm.state.value.error!!.contains("no longer available"))
        assertTrue("no LLM call should have been made", llmClient.requests.isEmpty())
    }

    @Test
    fun `two pages summarized in sequence keep their own content`() = runTest {
        pageTextCache.put("https://one.example", "first body")
        pageTextCache.put("https://two.example", "second body")

        viewModel().summarizePage("One", "https://one.example")
        viewModel().summarizePage("Two", "https://two.example")

        val requests = llmClient.requests.filterIsInstance<LlmRequest.SummarizePage>()
        assertEquals(listOf("first body", "second body"), requests.map { it.content })
    }

    @Test
    fun `each request shape titles its own document`() = runTest {
        engine.respond(body = storyWithComments)
        pageTextCache.put("https://p.example", "body")

        val summary = viewModel().also { it.summarizeStory(storyId) }
        val explanation = viewModel().also { it.explain("word", "Story") }
        val page = viewModel().also { it.summarizePage("P", "https://p.example") }

        assertEquals(LlmDocumentKind.STORY_SUMMARY, summary.state.value.kind)
        assertEquals(LlmDocumentKind.EXPLANATION, explanation.state.value.kind)
        assertEquals(LlmDocumentKind.PAGE_SUMMARY, page.state.value.kind)
    }

    @Test
    fun `a failure is reported and retry re-runs the request`() = runTest {
        llmClient.failure = IllegalStateException("upstream exploded")
        val vm = viewModel()
        vm.explain("word", "Story")

        assertEquals("upstream exploded", vm.state.value.error)

        llmClient.failure = null
        vm.refresh()

        assertEquals("an answer", vm.state.value.text)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `retry works when the story itself could not be loaded`() = runTest {
        engine.respond(code = 503, body = "upstream down")
        val vm = viewModel()
        vm.summarizeStory(storyId)
        assertTrue(vm.state.value.error != null)

        engine.respond(body = storyWithComments)
        vm.refresh()

        assertEquals("an answer", vm.state.value.text)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `retry works when the page text was missing and has since arrived`() = runTest {
        val vm = viewModel()
        vm.summarizePage("A Page", "https://late.example")
        assertTrue(vm.state.value.error != null)

        pageTextCache.put("https://late.example", "the body")
        vm.refresh()

        assertEquals("an answer", vm.state.value.text)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `copy of a story summary carries frontmatter, other documents carry plain text`() = runTest {
        engine.respond(body = storyWithComments)
        val summary = viewModel().also { it.summarizeStory(storyId) }
        val explanation = viewModel().also { it.explain("word", "Story") }

        assertTrue(summary.copyableText().startsWith("---"))
        assertTrue(summary.copyableText().contains("model: fake-model"))
        assertEquals("an answer", explanation.copyableText())
    }
}
