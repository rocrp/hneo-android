package dev.rocry.hneo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object LLMClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun streamCompletion(
        apiUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
    ): Flow<String> = callbackFlow {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                }
            }
            put("stream", true)
        }.toString()

        val request = Request.Builder()
            .url(apiUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            throw Exception("LLM API error: ${response.code}")
        }

        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))

        try {
            var line: String?
            while (true) {
                line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                try {
                    val chunk = json.parseToJsonElement(data).jsonObject
                    val choices = chunk["choices"]?.jsonArray ?: continue
                    if (choices.isEmpty()) continue
                    val delta = choices[0].jsonObject["delta"]?.jsonObject ?: continue
                    val content = delta["content"]?.jsonPrimitive?.contentOrNull ?: continue
                    trySend(content)
                } catch (_: Exception) {
                    // skip malformed chunks
                }
            }
        } finally {
            reader.close()
            response.close()
        }

        close()
        awaitClose()
    }
}
