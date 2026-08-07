package dev.rocry.hneo.ui.explain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rocry.hneo.data.llm.LlmClient
import dev.rocry.hneo.data.llm.LlmEvent
import dev.rocry.hneo.data.llm.LlmRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExplainState(
    val text: String = "",
    val isStreaming: Boolean = false,
    val model: String = "",
    val error: String? = null,
)

class ExplainViewModel(private val llmClient: LlmClient) : ViewModel() {
    private val _state = MutableStateFlow(ExplainState())
    val state = _state.asStateFlow()

    private var streamJob: Job? = null
    private var request: LlmRequest.Explain? = null

    fun explain(selectedText: String, storyTitle: String) {
        request = LlmRequest.Explain(selectedText = selectedText, storyTitle = storyTitle)
        stream()
    }

    fun refresh() = stream()

    private fun stream() {
        val explainRequest = request ?: return
        streamJob?.cancel()
        _state.value = ExplainState(isStreaming = true)

        streamJob = viewModelScope.launch {
            try {
                val buffer = StringBuilder()
                llmClient.stream(explainRequest).collect { event ->
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
                    error = e.message ?: "Failed to explain",
                )
            }
        }
    }
}
