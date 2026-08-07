package dev.rocry.hneo.data.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pins the real engine against a real socket — the fake engine covers everything above it. */
class OkHttpEngineTest {
    private lateinit var server: MockWebServer
    private lateinit var engine: OkHttpEngine

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        engine = OkHttpEngine(OkHttpClient(), Dispatchers.IO)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GET carries headers and reads the body`() = runTest {
        server.enqueue(MockResponse().setBody("hello"))

        val response = engine.execute(
            HttpRequest(server.url("/thing").toString(), headers = mapOf("Accept" to "text/plain")),
        )

        assertEquals(200, response.code)
        assertEquals("hello", response.readText())
        val recorded = server.takeRequest()
        assertEquals("/thing", recorded.path)
        assertEquals("text/plain", recorded.getHeader("Accept"))
    }

    @Test
    fun `POST sends the body with its content type`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        engine.execute(
            HttpRequest(
                url = server.url("/post").toString(),
                method = HttpMethod.POST,
                body = HttpBody("""{"a":1}"""),
            ),
        ).readText()

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("""{"a":1}""", recorded.body.readUtf8())
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test
    fun `non-2xx is delivered as a response, not thrown`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("slow down"))

        val response = engine.execute(HttpRequest(server.url("/limited").toString()))

        assertEquals(429, response.code)
        assertEquals("slow down", response.readText())
    }

    @Test
    fun `an unreachable host is a transport failure`() = runTest {
        val url = server.url("/gone").toString()
        server.shutdown()

        val engineFailure = runCatching { engine.execute(HttpRequest(url)) }.exceptionOrNull()

        assertEquals(HttpFailure.Transport::class, engineFailure!!::class)
    }
}
