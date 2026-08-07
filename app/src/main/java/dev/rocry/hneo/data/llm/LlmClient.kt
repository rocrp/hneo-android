package dev.rocry.hneo.data.llm

import dev.rocry.hneo.model.FlatComment
import dev.rocry.hneo.model.Story
import kotlinx.coroutines.flow.Flow

/**
 * What the app wants from an LLM, named by intent. A request carries everything
 * needed to serve it — no caller reaches back for content, prompts, or credentials.
 */
sealed interface LlmRequest {
    data class SummarizeStory(
        val story: Story,
        val comments: List<FlatComment>,
    ) : LlmRequest

    data class SummarizePage(
        val title: String,
        val url: String,
        val content: String,
    ) : LlmRequest

    data class Explain(
        val selectedText: String,
        val storyTitle: String,
    ) : LlmRequest
}

sealed interface LlmEvent {
    /** Emitted before the first chunk, so callers can show which model answered. */
    data class Started(val model: String) : LlmEvent

    data class Chunk(val text: String) : LlmEvent
}

sealed class LlmFailure(message: String) : Exception(message) {
    data object MissingApiKey :
        LlmFailure("API key not configured. Please set it in Settings.") {
        private fun readResolve(): Any = MissingApiKey
    }
}

/**
 * The one way this app talks to an LLM. The client owns settings lookup, prompt
 * selection, the API-key guard, and SSE parsing — callers only choose a request
 * and collect the answer.
 *
 * Failures arrive as [LlmFailure] or [dev.rocry.hneo.data.http.HttpFailure].
 */
interface LlmClient {
    fun stream(request: LlmRequest): Flow<LlmEvent>

    /**
     * The model [stream] would use right now. Callers need it to key caches and
     * caption results — but still never touch the settings themselves.
     */
    suspend fun currentModel(): String
}
