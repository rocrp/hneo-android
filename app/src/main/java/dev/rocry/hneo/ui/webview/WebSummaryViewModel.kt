package dev.rocry.hneo.ui.webview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rocry.hneo.data.AppSettings
import dev.rocry.hneo.data.LLMClient
import dev.rocry.hneo.data.settingsFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class WebSummaryState(
    val text: String = "",
    val isStreaming: Boolean = false,
    val model: String = "",
    val error: String? = null,
)

class WebSummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(WebSummaryState())
    val state = _state.asStateFlow()

    private var streamJob: Job? = null
    private var pageTitle = ""
    private var pageContent = ""
    private var pageUrl = ""

    fun startSummary(title: String, content: String, url: String) {
        pageTitle = title
        pageContent = content
        pageUrl = url

        viewModelScope.launch {
            val settings = settingsFlow(getApplication()).first()

            if (settings.llmApiKey.isBlank()) {
                _state.value = WebSummaryState(error = "API key not configured. Please set it in Settings.")
                return@launch
            }

            streamSummary(settings)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val settings = settingsFlow(getApplication()).first()
            streamSummary(settings)
        }
    }

    private fun streamSummary(settings: AppSettings) {
        streamJob?.cancel()
        _state.value = WebSummaryState(isStreaming = true, model = settings.llmModel)

        val truncated = pageContent.take(12_000)
        val userPrompt = buildString {
            appendLine("# $pageTitle")
            if (pageUrl.isNotBlank()) appendLine("URL: $pageUrl")
            appendLine()
            appendLine("## Page Content")
            appendLine()
            append(truncated)
        }

        streamJob = viewModelScope.launch {
            try {
                val buffer = StringBuilder()
                LLMClient.streamCompletion(
                    apiUrl = settings.llmApiUrl,
                    model = settings.llmModel,
                    apiKey = settings.llmApiKey,
                    systemPrompt = settings.llmWebpageSummaryPrompt,
                    userPrompt = userPrompt,
                ).collect { chunk ->
                    buffer.append(chunk)
                    _state.value = _state.value.copy(text = buffer.toString())
                }

                _state.value = _state.value.copy(isStreaming = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isStreaming = false,
                    error = e.message ?: "Failed to generate summary",
                )
            }
        }
    }
}
