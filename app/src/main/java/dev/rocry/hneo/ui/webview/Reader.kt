package dev.rocry.hneo.ui.webview

import dev.rocry.hneo.ui.theme.BuiltInFont
import java.util.Base64

/**
 * How reader mode should render text.
 *
 * [cssFontFace] is empty for fonts the device already has, and carries an
 * embedded `@font-face` for fonts the user imported — a WebView cannot read a
 * file out of app-private storage, so the bytes travel inside the stylesheet.
 */
data class ReaderFont(
    val cssFontFace: String,
    val cssFontFamily: String,
) {
    companion object {
        const val EMBEDDED_FAMILY = "CustomReaderFont"

        val SYSTEM = ReaderFont(cssFontFace = "", cssFontFamily = BuiltInFont.SYSTEM.cssFamily)

        fun builtIn(font: BuiltInFont) = ReaderFont(cssFontFace = "", cssFontFamily = font.cssFamily)

        /** Inlines an imported font so reader mode actually uses it. */
        fun embedded(fileName: String, bytes: ByteArray): ReaderFont {
            val extension = fileName.substringAfterLast('.', "ttf").lowercase()
            val format = if (extension == "otf") "opentype" else "truetype"
            val base64 = Base64.getEncoder().encodeToString(bytes)
            return ReaderFont(
                cssFontFace = "@font-face{font-family:\"$EMBEDDED_FAMILY\";" +
                    "src:url(\"data:font/$extension;base64,$base64\") format(\"$format\")}",
                cssFontFamily = "\"$EMBEDDED_FAMILY\",sans-serif",
            )
        }
    }
}

/**
 * Reader mode: which part of a page is the article, what it should look like,
 * and the script that replaces the page with it.
 */
object Reader {
    /**
     * Tried in this order; the first selector that matches wins. Order is the
     * point — a page with both `<main>` and `<article>` should read the article.
     */
    private val CONTENT_SELECTORS = listOf(
        "article",
        "[role=\"main\"]",
        "main",
        ".post-content, .article-content, .entry-content, .content",
    )

    /** Furniture that is never part of the article. */
    private const val CLUTTER_SELECTORS =
        "script, style, nav, footer, header, aside, iframe, " +
            ".ad, .ads, .sidebar, .comments, .social, .share, .related, .newsletter, .popup, " +
            ".modal, .cookie, [role=\"banner\"], [role=\"navigation\"], [role=\"complementary\"]"

    fun css(font: ReaderFont): String =
        font.cssFontFace +
            "body{max-width:680px;margin:0 auto;padding:20px 16px;" +
            "font-family:${font.cssFontFamily};" +
            "font-size:18px;line-height:1.8;color:#222;background:#fffff8}" +
            "img{max-width:100%;height:auto;border-radius:4px;margin:12px 0}" +
            "h1{font-size:24px;line-height:1.3;margin-bottom:16px}" +
            "a{color:#1a73e8}" +
            "pre,code{font-size:14px;background:#f5f5f5;padding:2px 6px;border-radius:3px;overflow-x:auto}" +
            "pre{padding:12px;margin:12px 0}" +
            "blockquote{border-left:3px solid #ddd;margin:12px 0;padding-left:16px;color:#555}" +
            "p{margin:0 0 16px}"

    fun script(font: ReaderFont): String {
        val stylesheet = css(font).escapeForJsSingleQuotes()
        val selectors = CONTENT_SELECTORS.joinToString(",") { "'${it.escapeForJsSingleQuotes()}'" }
        return """
            (function() {
                var selectors = [$selectors];
                var article = null;
                for (var s = 0; s < selectors.length && !article; s++) {
                    article = document.querySelector(selectors[s]);
                }
                var title = document.title;
                var temp = document.createElement('div');
                temp.innerHTML = article ? article.innerHTML : document.body.innerHTML;
                var clutter = temp.querySelectorAll('${CLUTTER_SELECTORS.escapeForJsSingleQuotes()}');
                for (var i = 0; i < clutter.length; i++) clutter[i].remove();
                document.head.innerHTML =
                    '<meta name="viewport" content="width=device-width, initial-scale=1">' +
                    '<style>$stylesheet</style>';
                document.body.innerHTML = '<h1>' + title + '</h1>' + temp.innerHTML;
            })()
        """.trimIndent()
    }

    private fun String.escapeForJsSingleQuotes(): String =
        replace("\\", "\\\\").replace("'", "\\'")
}
