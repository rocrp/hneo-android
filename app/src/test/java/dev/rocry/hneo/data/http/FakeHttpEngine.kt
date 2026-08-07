package dev.rocry.hneo.data.http

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** Canned [HttpResponse] that records whether callers closed it. */
class FakeHttpResponse(
    override val code: Int,
    private val body: String,
) : HttpResponse {
    var closed: Boolean = false
        private set

    override val contentLength: Long get() = body.toByteArray().size.toLong()

    override fun bodyStream(): InputStream = ByteArrayInputStream(body.toByteArray())

    override fun close() {
        closed = true
    }
}

/**
 * Drives any module built on [HttpEngine] without a socket. Records requests so
 * tests can assert on URLs, headers, and payloads.
 */
class FakeHttpEngine : HttpEngine {
    val requests = mutableListOf<HttpRequest>()
    val responses = mutableListOf<FakeHttpResponse>()

    private var handler: (HttpRequest) -> HttpResponse = {
        throw IllegalStateException("FakeHttpEngine has no queued response for ${it.url}")
    }

    val lastRequest: HttpRequest get() = requests.last()

    fun respond(code: Int = 200, body: String = "") {
        handler = { FakeHttpResponse(code, body) }
    }

    fun respondEachCall(vararg bodies: String) {
        var index = 0
        handler = { FakeHttpResponse(200, bodies[index++.coerceAtMost(bodies.lastIndex)]) }
    }

    fun failWith(error: IOException) {
        handler = { throw error }
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return handler(request).also { if (it is FakeHttpResponse) responses += it }
    }
}
