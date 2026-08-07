package dev.rocry.hneo.ui.llmdocument

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rocry.hneo.data.CachedSummary
import dev.rocry.hneo.data.PageTextCache
import dev.rocry.hneo.data.StoryRepository
import dev.rocry.hneo.data.SummaryCache
import dev.rocry.hneo.data.llm.LlmClient
import dev.rocry.hneo.data.llm.LlmEvent
import dev.rocry.hneo.data.llm.LlmRequest
import dev.rocry.hneo.model.Story
import dev.rocry.hneo.model.flattenComments
import dev.rocry.hneo.model.toStory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How a document announces itself. Derived from the request — never stored twice. */
enum class LlmDocumentKind(val title: String, val loadingCaption: String) {
    STORY_SUMMARY("AI Summary", "Generating summary..."),
    PAGE_SUMMARY("Page Summary", "Summarizing page..."),
    EXPLANATION("Explain", "Thinking..."),
}

data class LlmDocumentState(
    val kind: LlmDocumentKind = LlmDocumentKind.STORY_SUMMARY,
    val text: String = "",
    val isStreaming: Boolean = false,
    val isCached: Boolean = false,
    val model: String = "",
    val error: String? = null,
) {
    val hasContent: Boolean get() = text.isNotBlank()
}

/**
 * One ViewModel for every LLM Document. All three request shapes get the same
 * features — refresh, retry, copy, share, and a model subtitle — because there
 * is only one place left to implement them.
 */
class LlmDocumentViewModel(
    private val llmClient: LlmClient,
    private val storyRepository: StoryRepository,
    private val summaryCache: SummaryCache,
    private val pageTextCache: PageTextCache,
) : ViewModel() {

    private val _state = MutableStateFlow(LlmDocumentState())
    val state = _state.asStateFlow()

    private var streamJob: Job? = null
    private var request: LlmRequest? = null

    /**
     * How to produce this document again from the top. Set by whichever entry
     * point opened it, so refresh and retry-from-error work the same on every
     * shape — including the ones that fail before the LLM is ever reached.
     */
    private var reload: (() -> Unit)? = null

    /**
     * Pulls the story and its comment tree from the repository — the same cached
     * detail the comments screen is showing. Reading it here rather than being
     * handed it by the navigation layer is what stops summaries being generated
     * from zero comments.
     */
    fun summarizeStory(storyId: Int) = open {
        reload = { loadStorySummary(storyId, useCache = false) }
        loadStorySummary(storyId, useCache = true)
    }

    fun explain(selectedText: String, storyTitle: String) = open {
        val explainRequest = LlmRequest.Explain(selectedText, storyTitle)
        reload = { begin(explainRequest) }
        begin(explainRequest)
    }

    /**
     * Page content is looked up by URL rather than carried through navigation.
     * A miss is reported as one — the alternative is summarizing an empty string.
     */
    fun summarizePage(title: String, url: String) = open {
        reload = { loadPageSummary(title, url) }
        loadPageSummary(title, url)
    }

    /** Re-runs whatever produced this document, ignoring any cached answer. */
    fun refresh() {
        reload?.invoke()
    }

    fun copyableText(): String {
        val story = (request as? LlmRequest.SummarizeStory)?.story ?: return _state.value.text
        return frontMatter(story) + _state.value.text
    }

    /** Opening the same destination twice — a recomposition — must not re-bill the user. */
    private inline fun open(start: () -> Unit) {
        if (reload != null) return
        start()
    }

    private fun loadStorySummary(storyId: Int, useCache: Boolean) {
        streamJob?.cancel()
        _state.value = LlmDocumentState(kind = LlmDocumentKind.STORY_SUMMARY, isStreaming = true)

        streamJob = viewModelScope.launch {
            val detail = try {
                storyRepository.detail(storyId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LlmDocumentState(
                    kind = LlmDocumentKind.STORY_SUMMARY,
                    error = e.message ?: "Could not load the discussion",
                )
                return@launch
            }

            val story = detail.toStory()
            val storyRequest = LlmRequest.SummarizeStory(story, flattenComments(detail.comments))
            request = storyRequest

            if (useCache) {
                val cached = summaryCache.get(story.id, story.commentsCount, llmClient.currentModel())
                if (cached != null) {
                    _state.value = LlmDocumentState(
                        kind = LlmDocumentKind.STORY_SUMMARY,
                        text = cached.text,
                        isCached = true,
                        model = cached.model,
                    )
                    return@launch
                }
            }

            collect(storyRequest)
        }
    }

    private fun loadPageSummary(title: String, url: String) {
        val content = pageTextCache.get(url)
        if (content.isNullOrBlank()) {
            streamJob?.cancel()
            _state.value = LlmDocumentState(
                kind = LlmDocumentKind.PAGE_SUMMARY,
                error = "Page content is no longer available. Reopen the page and try again.",
            )
            return
        }
        begin(LlmRequest.SummarizePage(title = title, url = url, content = content))
    }

    private fun begin(newRequest: LlmRequest) {
        request = newRequest
        streamJob?.cancel()
        _state.value = LlmDocumentState(kind = newRequest.kind, isStreaming = true)
        streamJob = viewModelScope.launch { collect(newRequest) }
    }

    private suspend fun collect(current: LlmRequest) {
        try {
            val buffer = StringBuilder()
            llmClient.stream(current).collect { event ->
                when (event) {
                    is LlmEvent.Started -> _state.value = _state.value.copy(model = event.model)
                    is LlmEvent.Chunk -> {
                        buffer.append(event.text)
                        _state.value = _state.value.copy(text = buffer.toString())
                    }
                }
            }
            _state.value = _state.value.copy(isStreaming = false)

            if (current is LlmRequest.SummarizeStory) {
                summaryCache.put(
                    current.story.id,
                    CachedSummary(
                        text = buffer.toString(),
                        commentsCount = current.story.commentsCount,
                        model = _state.value.model,
                    ),
                )
            }
        } catch (e: CancellationException) {
            // A cancelled stream has been replaced by a newer one; leaving this to
            // the catch below would let the dead stream overwrite its successor.
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isStreaming = false,
                error = e.message ?: "Failed to generate ${current.kind.title}",
            )
        }
    }

    private fun frontMatter(story: Story): String = buildString {
        appendLine("---")
        appendLine("title: \"${story.title.replace("\"", "\\\"")}\"")
        story.url?.let { appendLine("source: $it") }
        appendLine("hn: https://news.ycombinator.com/item?id=${story.id}")
        story.points?.let { appendLine("score: $it") }
        story.user?.let { appendLine("author: $it") }
        appendLine("comments: ${story.commentsCount}")
        appendLine("model: ${_state.value.model}")
        appendLine("date: ${DATE_FORMAT.format(Date())}")
        appendLine("---")
        appendLine()
    }

    private companion object {
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}

internal val LlmRequest.kind: LlmDocumentKind
    get() = when (this) {
        is LlmRequest.SummarizeStory -> LlmDocumentKind.STORY_SUMMARY
        is LlmRequest.SummarizePage -> LlmDocumentKind.PAGE_SUMMARY
        is LlmRequest.Explain -> LlmDocumentKind.EXPLANATION
    }
