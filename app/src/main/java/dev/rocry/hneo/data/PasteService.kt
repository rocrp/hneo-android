package dev.rocry.hneo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object PasteService {
    private const val PASTE_URL = "https://paste.dzzu.net/api/pastes"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createPaste(content: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("content", content)
            put("format", "markdown")
        }.toString()

        val request = Request.Builder()
            .url(PASTE_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val result = json.parseToJsonElement(responseBody).jsonObject

        result["url"]?.jsonPrimitive?.contentOrNull
            ?: result["id"]?.jsonPrimitive?.contentOrNull?.let { "https://paste.dzzu.net/$it" }
            ?: throw Exception("Failed to create paste")
    }
}
