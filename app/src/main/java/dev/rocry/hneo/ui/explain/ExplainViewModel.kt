package dev.rocry.hneo.ui.explain

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

data class ExplainState(
    val text: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
)

class ExplainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ExplainState())
    val state = _state.asStateFlow()

    private var streamJob: Job? = null

    fun explain(selectedText: String, storyTitle: String, contextBefore: String = "", contextAfter: String = "") {
        streamJob?.cancel()
        _state.value = ExplainState(isStreaming = true)

        viewModelScope.launch {
            val settings = settingsFlow(getApplication()).first()

            if (settings.llmApiKey.isBlank()) {
                _state.value = ExplainState(error = "API key not configured. Please set it in Settings.")
                return@launch
            }

            val userPrompt = buildString {
                appendLine("Story: $storyTitle")
                if (contextBefore.isNotBlank()) appendLine("Context before: $contextBefore")
                appendLine("Selected text: $selectedText")
                if (contextAfter.isNotBlank()) appendLine("Context after: $contextAfter")
            }

            streamJob = launch {
                try {
                    val buffer = StringBuilder()
                    LLMClient.streamCompletion(
                        apiUrl = settings.llmApiUrl,
                        model = settings.llmModel,
                        apiKey = settings.llmApiKey,
                        systemPrompt = settings.llmExplainPrompt,
                        userPrompt = userPrompt,
                    ).collect { chunk ->
                        buffer.append(chunk)
                        _state.value = _state.value.copy(text = buffer.toString())
                    }
                    _state.value = _state.value.copy(isStreaming = false)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        isStreaming = false,
                        error = e.message ?: "Failed to explain",
                    )
                }
            }
        }
    }
}
