package dev.rocry.hneo.data

import dev.rocry.hneo.data.http.HttpBody
import dev.rocry.hneo.data.http.HttpMethod
import dev.rocry.hneo.data.http.HttpRequest
import dev.rocry.hneo.data.http.JsonHttp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Publishes markdown to a paste host so it can be shared as a link. */
class PasteService(private val http: JsonHttp) {

    suspend fun createPaste(content: String): String {
        val payload = buildJsonObject {
            put("content", content)
            put("format", "markdown")
        }.toString()

        val result = http.decodeObject(
            HttpRequest(
                url = PASTE_URL,
                method = HttpMethod.POST,
                body = HttpBody(payload),
            ),
        )

        return result["url"]?.jsonPrimitive?.contentOrNull
            ?: result["id"]?.jsonPrimitive?.contentOrNull?.let { "$HOST/$it" }
            ?: throw IllegalStateException("Paste host returned neither a url nor an id")
    }

    private companion object {
        const val HOST = "https://paste.dzzu.net"
        const val PASTE_URL = "$HOST/api/pastes"
    }
}
