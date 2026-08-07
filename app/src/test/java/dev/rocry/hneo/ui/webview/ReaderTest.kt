package dev.rocry.hneo.ui.webview

import dev.rocry.hneo.ui.theme.BuiltInFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ReaderTest {

    @Test
    fun `an imported font is embedded in the stylesheet and actually used`() {
        // The regression: a "simplify" commit gutted the embedded-font branch, so
        // imported fonts silently fell back to system-ui in reader mode.
        val bytes = byteArrayOf(0, 1, 2, 3, 4)

        val css = Reader.css(ReaderFont.embedded("Literata.ttf", bytes))

        assertTrue("stylesheet declares the face", css.contains("@font-face"))
        assertTrue(
            "stylesheet carries the font bytes",
            css.contains("base64,${Base64.getEncoder().encodeToString(bytes)}"),
        )
        assertTrue(
            "body actually asks for it",
            css.contains("font-family:\"${ReaderFont.EMBEDDED_FAMILY}\""),
        )
        assertFalse("and does not fall back to the system font", css.contains("system-ui"))
    }

    @Test
    fun `an embedded otf is declared as opentype, a ttf as truetype`() {
        val otf = ReaderFont.embedded("Serif.OTF", byteArrayOf(1))
        val ttf = ReaderFont.embedded("Serif.ttf", byteArrayOf(1))

        assertTrue(otf.cssFontFace.contains("data:font/otf"))
        assertTrue(otf.cssFontFace.contains("format(\"opentype\")"))
        assertTrue(ttf.cssFontFace.contains("data:font/ttf"))
        assertTrue(ttf.cssFontFace.contains("format(\"truetype\")"))
    }

    @Test
    fun `built-in fonts need no font-face and use their own family`() {
        BuiltInFont.entries.forEach { font ->
            val css = Reader.css(ReaderFont.builtIn(font))
            assertFalse("${font.displayName} should not embed anything", css.contains("@font-face"))
            assertTrue("${font.displayName} should ask for its own family", css.contains(font.cssFamily))
        }
    }

    @Test
    fun `the script embeds the stylesheet it was given`() {
        val script = Reader.script(ReaderFont.builtIn(BuiltInFont.SERIF))

        assertTrue(script.contains("Georgia,serif"))
        assertTrue(script.contains("<style>"))
        assertTrue(script.contains("document.body.innerHTML"))
    }

    @Test
    fun `the script prefers an article over a main element`() {
        val script = Reader.script(ReaderFont.SYSTEM)

        val articleAt = script.indexOf("'article'")
        val mainAt = script.indexOf("'main'")
        assertTrue("both selectors are present", articleAt >= 0 && mainAt >= 0)
        assertTrue("article is tried first", articleAt < mainAt)
    }

    @Test
    fun `a font name containing a quote cannot break out of the script`() {
        val script = Reader.script(ReaderFont(cssFontFace = "", cssFontFamily = "it's,sans-serif"))

        assertTrue(script.contains("it\\'s"))
    }

    @Test
    fun `the system reader font is the system font family`() {
        assertEquals(BuiltInFont.SYSTEM.cssFamily, ReaderFont.SYSTEM.cssFontFamily)
        assertEquals("", ReaderFont.SYSTEM.cssFontFace)
    }
}
