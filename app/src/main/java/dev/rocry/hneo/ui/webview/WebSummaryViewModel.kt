package dev.rocry.hneo.ui.webview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rocry.hneo.data.llm.LlmClient
import dev.rocry.hneo.data.llm.LlmEvent
import dev.rocry.hneo.data.llm.LlmRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WebSummaryState(
    val text: String = "",
    val isStreaming: Boolean = false,
    val model: String = "",
    val error: String? = null,
)

class WebSummaryViewModel(private val llmClient: LlmClient) : ViewModel() {
    private val _state = MutableStateFlow(WebSummaryState())
    val state = _state.asStateFlow()

    private var streamJob: Job? = null
    private var request: LlmRequest.SummarizePage? = null

    fun startSummary(title: String, content: String, url: String) {
        request = LlmRequest.SummarizePage(title = title, url = url, content = content)
        streamSummary()
    }

    fun refresh() = streamSummary()

    private fun streamSummary() {
        val pageRequest = request ?: return
        streamJob?.cancel()
        _state.value = WebSummaryState(isStreaming = true)

        streamJob = viewModelScope.launch {
            try {
                val buffer = StringBuilder()
                llmClient.stream(pageRequest).collect { event ->
                    when (event) {
                        is LlmEvent.Started -> _state.value = _state.value.copy(model = event.model)
                        is LlmEvent.Chunk -> {
                            buffer.append(event.text)
                            _state.value = _state.value.copy(text = buffer.toString())
                        }
                    }
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
