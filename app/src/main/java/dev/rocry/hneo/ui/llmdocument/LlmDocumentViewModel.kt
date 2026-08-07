package dev.rocry.hneo.ui.llmdocument

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rocry.hneo.data.CachedSummary
import dev.rocry.hneo.data.PageTextCache
import dev.rocry.hneo.data.SummaryCache
import dev.rocry.hneo.data.llm.LlmClient
import dev.rocry.hneo.data.llm.LlmEvent
import dev.rocry.hneo.data.llm.LlmRequest
import dev.rocry.hneo.model.FlatComment
import dev.rocry.hneo.model.Story
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
    private val summaryCache: SummaryCache,
    private val pageTextCache: PageTextCache,
) : ViewModel() {

    private val _state = MutableStateFlow(LlmDocumentState())
    val state = _state.asStateFlow()

    private var streamJob: Job? = null
    private var request: LlmRequest? = null

    fun summarizeStory(story: Story, comments: List<FlatComment>) =
        start(LlmRequest.SummarizeStory(story, comments))

    fun explain(selectedText: String, storyTitle: String) =
        start(LlmRequest.Explain(selectedText, storyTitle))

    /**
     * Page content is looked up by URL rather than carried through navigation.
     * A miss is reported as one — the alternative is summarizing an empty string.
     */
    fun summarizePage(title: String, url: String) {
        val content = pageTextCache.get(url)
        if (content.isNullOrBlank()) {
            _state.value = LlmDocumentState(
                kind = LlmDocumentKind.PAGE_SUMMARY,
                error = "Page content is no longer available. Reopen the page and try again.",
            )
            return
        }
        start(LlmRequest.SummarizePage(title = title, url = url, content = content))
    }

    /** Re-runs the request from scratch, ignoring any cached answer. */
    fun refresh() {
        if (request != null) stream()
    }

    fun copyableText(): String {
        val current = request
        if (current !is LlmRequest.SummarizeStory) return _state.value.text
        return buildString {
            val story = current.story
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
            append(_state.value.text)
        }
    }

    private fun start(newRequest: LlmRequest) {
        // Restarting on recomposition would re-bill the user for an answer they
        // are already looking at.
        if (request == newRequest) return
        request = newRequest

        val story = (newRequest as? LlmRequest.SummarizeStory)?.story
        if (story == null) {
            stream()
            return
        }

        viewModelScope.launch {
            val cached = summaryCache.get(story.id, story.commentsCount, llmClient.currentModel())
            if (cached != null) {
                _state.value = LlmDocumentState(
                    kind = newRequest.kind,
                    text = cached.text,
                    isCached = true,
                    model = cached.model,
                )
            } else {
                stream()
            }
        }
    }

    private fun stream() {
        val current = request ?: return
        streamJob?.cancel()
        _state.value = LlmDocumentState(kind = current.kind, isStreaming = true)

        streamJob = viewModelScope.launch {
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
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isStreaming = false,
                    error = e.message ?: "Failed to generate ${current.kind.title}",
                )
            }
        }
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
