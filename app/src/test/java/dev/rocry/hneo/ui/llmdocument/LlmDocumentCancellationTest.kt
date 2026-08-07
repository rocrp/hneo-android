package dev.rocry.hneo.ui.llmdocument

import dev.rocry.hneo.MainDispatcherRule
import dev.rocry.hneo.data.CommentCache
import dev.rocry.hneo.data.HNClient
import dev.rocry.hneo.data.PageTextCache
import dev.rocry.hneo.data.StoryCache
import dev.rocry.hneo.data.StoryRepository
import dev.rocry.hneo.data.SummaryCache
import dev.rocry.hneo.data.http.FakeHttpEngine
import dev.rocry.hneo.data.http.JsonHttp
import dev.rocry.hneo.data.llm.FakeLlmClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Cancellation on a *queued* dispatcher, so a cancelled stream resumes after its
 * replacement has already published state. On an unconfined dispatcher the two
 * happen in the wrong order to catch the bug.
 */
class LlmDocumentCancellationTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }
    private val llmClient = FakeLlmClient()

    private fun viewModel(): LlmDocumentViewModel {
        val engine = FakeHttpEngine()
        return LlmDocumentViewModel(
            llmClient = llmClient,
            storyRepository = StoryRepository(
                hnClient = HNClient(JsonHttp(engine, json, dispatcher)),
                storyCache = StoryCache(File(tempFolder.root, "stories"), json, dispatcher),
                commentCache = CommentCache(),
            ),
            summaryCache = SummaryCache(File(tempFolder.root, "summaries.json"), json, dispatcher),
            pageTextCache = PageTextCache(),
        )
    }

    @Test
    fun `a cancelled stream neither keeps running nor reports over its replacement`() = runTest(dispatcher) {
        llmClient.holdOpen = CompletableDeferred()
        val vm = viewModel()

        vm.explain("word", "Story")
        advanceUntilIdle()
        assertEquals("an answer", vm.state.value.text)

        llmClient.holdOpen = null
        llmClient.reply = "second answer"
        vm.refresh()
        advanceUntilIdle()

        assertTrue("the first stream was actually cancelled", llmClient.cancelled.isCompleted)
        assertEquals("second answer", vm.state.value.text)
        assertNull("a dead stream must not report an error over its successor", vm.state.value.error)
        assertEquals(2, llmClient.requests.size)
    }
}
