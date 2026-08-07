package dev.rocry.hneo.data.llm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLlmClient(
    var model: String = "fake-model",
    var reply: String = "an answer",
) : LlmClient {
    val requests = mutableListOf<LlmRequest>()
    var failure: Throwable? = null

    /** When set, the stream emits its reply and then hangs until this completes. */
    var holdOpen: CompletableDeferred<Unit>? = null

    /** Completed when a held-open stream is cancelled, so tests can prove it stopped. */
    val cancelled = CompletableDeferred<Unit>()

    val lastRequest: LlmRequest get() = requests.last()

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        requests += request
        failure?.let { throw it }
        emit(LlmEvent.Started(model))
        emit(LlmEvent.Chunk(reply))
        val hold = holdOpen
        if (hold != null) {
            try {
                hold.await()
            } finally {
                cancelled.complete(Unit)
            }
        }
    }

    override suspend fun currentModel(): String = model
}
