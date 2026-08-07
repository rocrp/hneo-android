package dev.rocry.hneo.data.llm

import dev.rocry.hneo.data.AppSettings
import dev.rocry.hneo.data.SettingsStore
import dev.rocry.hneo.data.http.HttpBody
import dev.rocry.hneo.data.http.HttpEngine
import dev.rocry.hneo.data.http.HttpFailure
import dev.rocry.hneo.data.http.HttpMethod
import dev.rocry.hneo.data.http.HttpRequest
import dev.rocry.hneo.data.http.isSuccessful
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Streams from any OpenAI-compatible chat-completions endpoint. */
class OpenAiLlmClient(
    private val engine: HttpEngine,
    private val json: Json,
    private val settingsStore: SettingsStore,
    private val ioDispatcher: CoroutineDispatcher,
) : LlmClient {

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        val settings = settingsStore.settings.first()
        if (settings.llmApiKey.isBlank()) throw LlmFailure.MissingApiKey

        emit(LlmEvent.Started(settings.llmModel))

        engine.execute(request.toHttpRequest(settings)).use { response ->
            val stream = response.bodyStream()
            if (!response.isSuccessful) {
                // The API explains itself in the body; a bare status code cannot
                // tell a bad key from a bad model.
                throw HttpFailure.Status(response.code, stream.bufferedReader().readText())
            }

            stream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    when (val chunk = parseSseLine(line, json)) {
                        is SseChunk.Content -> emit(LlmEvent.Chunk(chunk.text))
                        SseChunk.Done -> break
                        SseChunk.Skip -> Unit
                    }
                }
            }
        }
    }.flowOn(ioDispatcher)

    override suspend fun currentModel(): String = settingsStore.settings.first().llmModel

    private fun LlmRequest.toHttpRequest(settings: AppSettings) = HttpRequest(
        url = settings.llmApiUrl,
        method = HttpMethod.POST,
        headers = mapOf("Authorization" to "Bearer ${settings.llmApiKey}"),
        body = HttpBody(completionPayload(settings)),
        readTimeoutSeconds = STREAM_TIMEOUT_SECONDS,
    )

    private fun LlmRequest.completionPayload(settings: AppSettings): String = buildJsonObject {
        put("model", settings.llmModel)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", systemPrompt(settings))
            }
            addJsonObject {
                put("role", "user")
                put("content", userPrompt(settings))
            }
        }
        put("stream", true)
    }.toString()

    private companion object {
        const val STREAM_TIMEOUT_SECONDS = 60L
    }
}
