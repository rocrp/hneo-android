package dev.rocry.hneo.data.http

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * The production [HttpEngine]. One [OkHttpClient] — per-call timeout overrides go
 * through `newBuilder()`, which shares the connection pool and dispatcher.
 */
class OkHttpEngine(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) : HttpEngine {

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(ioDispatcher) {
        val call = clientFor(request).newCall(request.toOkHttp())
        try {
            OkHttpResponse(call.execute())
        } catch (e: IOException) {
            throw HttpFailure.Transport(e)
        }
    }

    private fun clientFor(request: HttpRequest): OkHttpClient =
        if (request.readTimeoutSeconds == HttpRequest.DEFAULT_READ_TIMEOUT_SECONDS) {
            client
        } else {
            client.newBuilder()
                .readTimeout(request.readTimeoutSeconds, TimeUnit.SECONDS)
                .build()
        }

    private fun HttpRequest.toOkHttp(): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        when (method) {
            HttpMethod.GET -> builder.get()
            HttpMethod.POST -> {
                val payload = body ?: HttpBody("")
                builder.post(payload.content.toRequestBody(payload.contentType.toMediaType()))
            }
        }
        return builder.build()
    }

    private class OkHttpResponse(private val response: Response) : HttpResponse {
        override val code: Int get() = response.code

        override val contentLength: Long get() = response.body?.contentLength() ?: -1L

        override fun bodyStream(): InputStream =
            response.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))

        override fun close() = response.close()
    }
}
