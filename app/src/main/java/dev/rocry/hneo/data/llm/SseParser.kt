package dev.rocry.hneo.data.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What one line of an OpenAI-style SSE stream means. */
internal sealed interface SseChunk {
    data class Content(val text: String) : SseChunk

    /** The server said it is finished. */
    data object Done : SseChunk

    /** Keep-alives, comments, and chunks we cannot read — never fatal. */
    data object Skip : SseChunk
}

private const val DATA_PREFIX = "data: "
private const val DONE_SENTINEL = "[DONE]"

internal fun parseSseLine(line: String, json: Json): SseChunk {
    if (!line.startsWith(DATA_PREFIX)) return SseChunk.Skip

    val data = line.removePrefix(DATA_PREFIX).trim()
    if (data == DONE_SENTINEL) return SseChunk.Done

    val content = try {
        json.parseToJsonElement(data)
            .jsonObject["choices"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject?.get("delta")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
    } catch (_: Exception) {
        null
    }

    return content?.let(SseChunk::Content) ?: SseChunk.Skip
}
