package dev.rocry.hneo.data.http

import java.io.Closeable
import java.io.IOException
import java.io.InputStream

enum class HttpMethod { GET, POST }

data class HttpBody(val content: String, val contentType: String = "application/json")

data class HttpRequest(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val body: HttpBody? = null,
    /** Per-call read timeout. Streaming callers need longer than the default. */
    val readTimeoutSeconds: Long = DEFAULT_READ_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_READ_TIMEOUT_SECONDS = 15L
    }
}

/**
 * A response whose body is streamed, so large downloads and SSE never buffer whole.
 * Callers must [close] it; [readText] closes it for them.
 */
interface HttpResponse : Closeable {
    val code: Int

    /** -1 when the server sent no Content-Length. */
    val contentLength: Long

    fun bodyStream(): InputStream
}

val HttpResponse.isSuccessful: Boolean get() = code in 200..299

/**
 * The one way this app talks HTTP. Implementations own connection pooling and
 * threading; everything above them is transport-agnostic and therefore testable.
 */
interface HttpEngine {
    suspend fun execute(request: HttpRequest): HttpResponse
}

/**
 * The three ways an HTTP call can fail, kept distinguishable so callers can tell
 * "the server said no" from "the server said something we can't read" from
 * "we never reached the server".
 */
sealed class HttpFailure(message: String, cause: Throwable?) : IOException(message, cause) {
    /** Non-2xx. Carries the error body — that is where APIs explain themselves. */
    class Status(val code: Int, val body: String) : HttpFailure(describe(code, body), null)

    /** 2xx whose body did not match the expected shape. */
    class Malformed(cause: Throwable) : HttpFailure("Unexpected response format from the server", cause)

    /** Never got a response at all. */
    class Transport(cause: Throwable) : HttpFailure(cause.message ?: "Network unavailable", cause)

    companion object {
        private const val MAX_DETAIL = 300

        private fun describe(code: Int, body: String): String {
            val detail = body.trim().take(MAX_DETAIL)
            return if (detail.isEmpty()) "HTTP $code" else "HTTP $code: $detail"
        }
    }
}
