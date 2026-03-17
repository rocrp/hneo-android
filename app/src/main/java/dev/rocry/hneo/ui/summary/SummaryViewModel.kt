package dev.rocry.hneo.ui.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rocry.hneo.data.*
import dev.rocry.hneo.model.FlatComment
import dev.rocry.hneo.model.Story
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class SummaryState(
    val text: String = "",
    val isStreaming: Boolean = false,
    val isCached: Boolean = false,
    val model: String = "",
    val error: String? = null,
)

class SummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SummaryState())
    val state = _state.asStateFlow()

    private val summaryCache = SummaryCache(application)
    private var streamJob: Job? = null
    private var currentStory: Story? = null
    private var currentComments: List<FlatComment> = emptyList()

    init {
        viewModelScope.launch { summaryCache.load() }
    }

    fun startSummary(story: Story, comments: List<FlatComment>) {
        currentStory = story
        currentComments = comments

        viewModelScope.launch {
            val settings = settingsFlow(getApplication()).first()

            if (settings.llmApiKey.isBlank()) {
                _state.value = SummaryState(error = "API key not configured. Please set it in Settings.")
                return@launch
            }

            // Check cache
            val cached = summaryCache.get(story.id, story.commentsCount, settings.llmModel)
            if (cached != null) {
                _state.value = SummaryState(
                    text = cached.text,
                    isCached = true,
                    model = cached.model,
                )
                return@launch
            }

            streamSummary(story, comments, settings)
        }
    }

    fun refresh() {
        val story = currentStory ?: return
        viewModelScope.launch {
            val settings = settingsFlow(getApplication()).first()
            streamSummary(story, currentComments, settings)
        }
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

    private fun streamSummary(story: Story, comments: List<FlatComment>, settings: AppSettings) {
        streamJob?.cancel()
        _state.value = SummaryState(isStreaming = true, model = settings.llmModel)

        val maxComments = settings.llmMaxComments
        val truncated = comments.take(maxComments)

        val userPrompt = buildString {
            appendLine("[Story] ${story.title}")
            story.url?.let { appendLine("[URL] $it") }
            appendLine("[Score] ${story.points ?: 0} | [Comments] ${story.commentsCount}")
            appendLine()
            for (c in truncated) {
                val indent = "  ".repeat(c.depth)
                appendLine("$indent[${c.user}] ${c.text}")
            }
        }

        streamJob = viewModelScope.launch {
            try {
                val buffer = StringBuilder()
                LLMClient.streamCompletion(
                    apiUrl = settings.llmApiUrl,
                    model = settings.llmModel,
                    apiKey = settings.llmApiKey,
                    systemPrompt = settings.llmSystemPrompt,
                    userPrompt = userPrompt,
                ).collect { chunk ->
                    buffer.append(chunk)
                    _state.value = _state.value.copy(text = buffer.toString())
                }

                _state.value = _state.value.copy(isStreaming = false)

                // Cache the result
                summaryCache.put(
                    story.id,
                    CachedSummary(
                        text = buffer.toString(),
                        commentsCount = story.commentsCount,
                        model = settings.llmModel,
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
