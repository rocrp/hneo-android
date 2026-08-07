package dev.rocry.hneo.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rocry.hneo.data.CachedSummary
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

data class SummaryState(
    val text: String = "",
    val isStreaming: Boolean = false,
    val isCached: Boolean = false,
    val model: String = "",
    val error: String? = null,
)

class SummaryViewModel(
    private val llmClient: LlmClient,
    private val summaryCache: SummaryCache,
) : ViewModel() {
    private val _state = MutableStateFlow(SummaryState())
    val state = _state.asStateFlow()

    private var streamJob: Job? = null
    private var currentStory: Story? = null
    private var currentComments: List<FlatComment> = emptyList()

    fun startSummary(story: Story, comments: List<FlatComment>) {
        currentStory = story
        currentComments = comments

        viewModelScope.launch {
            val model = llmClient.currentModel()
            val cached = summaryCache.get(story.id, story.commentsCount, model)
            if (cached != null) {
                _state.value = SummaryState(text = cached.text, isCached = true, model = cached.model)
                return@launch
            }
            streamSummary(story, comments)
        }
    }

    fun refresh() {
        val story = currentStory ?: return
        streamSummary(story, currentComments)
    }

    fun buildMarkdown(): String {
        val story = currentStory ?: return _state.value.text
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return buildString {
            appendLine("---")
            appendLine("title: \"${story.title.replace("\"", "\\\"")}\"")
            story.url?.let { appendLine("source: $it") }
            appendLine("hn: https://news.ycombinator.com/item?id=${story.id}")
            story.points?.let { appendLine("score: $it") }
            story.user?.let { appendLine("author: $it") }
            appendLine("comments: ${story.commentsCount}")
            appendLine("model: ${_state.value.model}")
            appendLine("date: ${dateFormat.format(Date())}")
            appendLine("---")
            appendLine()
            append(_state.value.text)
        }
    }

    private fun streamSummary(story: Story, comments: List<FlatComment>) {
        streamJob?.cancel()
        _state.value = SummaryState(isStreaming = true)

        streamJob = viewModelScope.launch {
            try {
                val buffer = StringBuilder()
                llmClient.stream(LlmRequest.SummarizeStory(story, comments)).collect { event ->
                    when (event) {
                        is LlmEvent.Started -> _state.value = _state.value.copy(model = event.model)
                        is LlmEvent.Chunk -> {
                            buffer.append(event.text)
                            _state.value = _state.value.copy(text = buffer.toString())
                        }
                    }
                }
                _state.value = _state.value.copy(isStreaming = false)

                summaryCache.put(
                    story.id,
                    CachedSummary(
                        text = buffer.toString(),
                        commentsCount = story.commentsCount,
                        model = _state.value.model,
                    ),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isStreaming = false,
                    error = e.message ?: "Failed to generate summary",
                )
            }
        }
    }
}
