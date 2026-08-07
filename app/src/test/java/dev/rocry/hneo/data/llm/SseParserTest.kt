package dev.rocry.hneo.data.llm

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(line: String) = parseSseLine(line, json)

    private fun contentLine(text: String) =
        """data: {"choices":[{"delta":{"content":"$text"}}]}"""

    @Test
    fun `reads the delta content out of a chunk`() {
        assertEquals(SseChunk.Content("Hello"), parse(contentLine("Hello")))
    }

    @Test
    fun `accumulating chunks reconstructs the message`() {
        val lines = listOf("The ", "quick ", "fox").map(::contentLine) + "data: [DONE]"

        val accumulated = buildString {
            for (line in lines) {
                when (val chunk = parse(line)) {
                    is SseChunk.Content -> append(chunk.text)
                    SseChunk.Done -> break
                    SseChunk.Skip -> Unit
                }
            }
        }

        assertEquals("The quick fox", accumulated)
    }

    @Test
    fun `the done sentinel ends the stream`() {
        assertEquals(SseChunk.Done, parse("data: [DONE]"))
    }

    @Test
    fun `a malformed chunk is skipped, not fatal`() {
        assertEquals(SseChunk.Skip, parse("data: {not json"))
    }

    @Test
    fun `chunks without content are skipped`() {
        assertEquals(SseChunk.Skip, parse("""data: {"choices":[{"delta":{}}]}"""))
        assertEquals(SseChunk.Skip, parse("""data: {"choices":[]}"""))
        assertEquals(SseChunk.Skip, parse("""data: {}"""))
    }

    @Test
    fun `non-data lines are skipped`() {
        assertEquals(SseChunk.Skip, parse(""))
        assertEquals(SseChunk.Skip, parse(": keep-alive"))
        assertEquals(SseChunk.Skip, parse("event: message"))
    }

    @Test
    fun `an empty content delta still counts as content`() {
        assertEquals(SseChunk.Content(""), parse(contentLine("")))
    }
}
