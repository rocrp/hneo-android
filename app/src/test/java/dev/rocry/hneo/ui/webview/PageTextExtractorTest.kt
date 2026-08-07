package dev.rocry.hneo.ui.webview

import org.junit.Assert.assertEquals
import org.junit.Test

class PageTextExtractorTest {

    private fun decode(raw: String?) = PageTextExtractor.decode(raw)

    @Test
    fun `plain text loses its surrounding quotes`() {
        assertEquals("Hello world", decode("\"Hello world\""))
    }

    @Test
    fun `newlines and tabs are unescaped`() {
        assertEquals("line one\nline two\tindented", decode("\"line one\\nline two\\tindented\""))
    }

    @Test
    fun `a backslash survives instead of eating the next character`() {
        // The old decoder turned C:\\nginx into "C:" + newline + "ginx".
        assertEquals("""C:\nginx\config""", decode(""""C:\\nginx\\config""""))
    }

    @Test
    fun `carriage returns are unescaped`() {
        assertEquals("windows\r\nline", decode("\"windows\\r\\nline\""))
    }

    @Test
    fun `unicode escapes become their characters`() {
        assertEquals("caf\u00e9 \u4e2d\u6587", decode("\"caf\\u00e9 \\u4e2d\\u6587\""))
    }

    @Test
    fun `escaped quotes and slashes are unescaped`() {
        assertEquals("""say "hi" at http://a.example""", decode(""""say \"hi\" at http:\/\/a.example""""))
    }

    @Test
    fun `form feed and backspace are unescaped`() {
        assertEquals("a\bb\u000Cc", decode("\"a\\bb\\fc\""))
    }

    @Test
    fun `a truncated unicode escape is left alone rather than crashing`() {
        assertEquals("u12", decode("\"\\u12\""))
    }

    @Test
    fun `a trailing backslash does not overrun the string`() {
        assertEquals("ends with", decode("\"ends with\\\""))
    }

    @Test
    fun `a null or empty result is empty text`() {
        assertEquals("", decode(null))
        assertEquals("", decode("null"))
        assertEquals("", decode(""))
        assertEquals("", decode("\"\""))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("body", decode("\"\\n\\n  body  \\n\""))
    }

    @Test
    fun `an unquoted result is passed through`() {
        assertEquals("undefined", decode("undefined"))
    }
}
