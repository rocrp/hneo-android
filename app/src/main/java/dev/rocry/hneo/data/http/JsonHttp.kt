package dev.rocry.hneo.data.http

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import java.io.IOException

/**
 * Request → status check → decode, in one place.
 *
 * Every JSON-speaking module goes through here, so "non-2xx" can never again be
 * handed to the decoder and surface to the user as a parser message.
 */
class JsonHttp(
    private val engine: HttpEngine,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /** @throws HttpFailure.Status on non-2xx, [HttpFailure.Transport] if unreachable. */
    suspend fun text(request: HttpRequest): String = withContext(ioDispatcher) {
        val response = try {
            engine.execute(request)
        } catch (e: HttpFailure) {
            throw e
        } catch (e: IOException) {
            throw HttpFailure.Transport(e)
        }

        response.use {
            val body = try {
                it.bodyStream().bufferedReader().readText()
            } catch (e: IOException) {
                throw HttpFailure.Transport(e)
            }
            if (!it.isSuccessful) throw HttpFailure.Status(it.code, body)
            body
        }
    }

    /** @throws HttpFailure.Malformed when a 2xx body does not match [deserializer]. */
    suspend fun <T> decode(request: HttpRequest, deserializer: DeserializationStrategy<T>): T {
        val body = text(request)
        return try {
            json.decodeFromString(deserializer, body)
        } catch (e: SerializationException) {
            throw HttpFailure.Malformed(e)
        }
    }

    /** For APIs whose shape is too loose to model — still status-checked and failure-typed. */
    suspend fun decodeObject(request: HttpRequest): JsonObject = decode(request, serializer())
}

suspend inline fun <reified T> JsonHttp.decode(request: HttpRequest): T =
    decode(request, serializer())
