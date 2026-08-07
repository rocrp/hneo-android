package dev.rocry.hneo.data.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLlmClient(
    var model: String = "fake-model",
    var reply: String = "an answer",
) : LlmClient {
    val requests = mutableListOf<LlmRequest>()
    var failure: Throwable? = null

    val lastRequest: LlmRequest get() = requests.last()

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        requests += request
        failure?.let { throw it }
        emit(LlmEvent.Started(model))
        emit(LlmEvent.Chunk(reply))
    }

    override suspend fun currentModel(): String = model
}
