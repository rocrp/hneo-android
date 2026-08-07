package dev.rocry.hneo.ui.llmdocument

import dev.rocry.hneo.MainDispatcherRule
import dev.rocry.hneo.data.CachedSummary
import dev.rocry.hneo.data.PageTextCache
import dev.rocry.hneo.data.SummaryCache
import dev.rocry.hneo.data.llm.FakeLlmClient
import dev.rocry.hneo.data.llm.LlmRequest
import dev.rocry.hneo.model.FlatComment
import dev.rocry.hneo.model.Story
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
    private val llmClient = FakeLlmClient()
    private val pageTextCache = PageTextCache()

    private val story = Story(id = 7, title = "Story", commentsCount = 3)

    private fun summaryCacheWith(vararg entries: Pair<Int, CachedSummary>): SummaryCache {
        val file = File(tempFolder.root, "summaries.json")
        if (entries.isNotEmpty()) {
            file.writeText(json.encodeToString(entries.associate { it.first.toString() to it.second }))
        }
        return SummaryCache(file, json, UnconfinedTestDispatcher())
    }

    private fun viewModel(summaryCache: SummaryCache = summaryCacheWith()) =
        LlmDocumentViewModel(llmClient, summaryCache, pageTextCache)

    @Test
    fun `a cold open of a previously summarized story serves the cache`() = runTest {
        // The load-before-get race used to make this miss and re-bill the user.
        val cache = summaryCacheWith(
            7 to CachedSummary(text = "cached answer", commentsCount = 3, model = "fake-model"),
        )

        val vm = viewModel(cache)
        vm.summarizeStory(story, emptyList())

        assertEquals("cached answer", vm.state.value.text)
        assertTrue(vm.state.value.isCached)
        assertTrue("no LLM call should have been made", llmClient.requests.isEmpty())
    }

    @Test
    fun `a summary cached under a different model is not reused`() = runTest {
        val cache = summaryCacheWith(
            7 to CachedSummary(text = "old", commentsCount = 3, model = "some-other-model"),
        )

        val vm = viewModel(cache)
        vm.summarizeStory(story, emptyList())

        assertEquals("an answer", vm.state.value.text)
        assertFalse(vm.state.value.isCached)
    }

    @Test
    fun `a fresh summary is written to the cache and served on the next open`() = runTest {
        viewModel(summaryCacheWith()).summarizeStory(story, emptyList())
        llmClient.requests.clear()

        // A brand-new cache over the same file — this is the cold-open path.
        val reopened = viewModel(summaryCacheWith())
        reopened.summarizeStory(story, emptyList())

        assertEquals("an answer", reopened.state.value.text)
        assertTrue(reopened.state.value.isCached)
        assertTrue(llmClient.requests.isEmpty())
    }

    @Test
    fun `a story summary request carries the comments it was given`() = runTest {
        val comments = listOf(FlatComment(1, "alice", "1h", "hello", 0, 0))

        viewModel().summarizeStory(story, comments)

        val request = llmClient.lastRequest as LlmRequest.SummarizeStory
        assertEquals(comments, request.comments)
    }

    @Test
    fun `restarting with the same request does not re-bill`() = runTest {
        val vm = viewModel()

        vm.summarizeStory(story, emptyList())
        vm.summarizeStory(story, emptyList())

        assertEquals(1, llmClient.requests.size)
    }

    @Test
    fun `refresh re-runs the request even when it was served from cache`() = runTest {
        val cache = summaryCacheWith(
            7 to CachedSummary(text = "cached", commentsCount = 3, model = "fake-model"),
        )
        val vm = viewModel(cache)
        vm.summarizeStory(story, emptyList())

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
        val summary = viewModel().also { it.summarizeStory(story, emptyList()) }
        val explanation = viewModel().also { it.explain("word", "Story") }
        pageTextCache.put("https://p.example", "body")
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
    fun `copy of a story summary carries frontmatter, other documents carry plain text`() = runTest {
        val summary = viewModel().also { it.summarizeStory(story, emptyList()) }
        val explanation = viewModel().also { it.explain("word", "Story") }

        assertTrue(summary.copyableText().startsWith("---"))
        assertTrue(summary.copyableText().contains("model: fake-model"))
        assertEquals("an answer", explanation.copyableText())
    }
}
