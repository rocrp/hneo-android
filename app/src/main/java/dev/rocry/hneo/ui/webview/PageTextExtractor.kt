package dev.rocry.hneo.ui.webview

/**
 * Pulls the readable text out of a loaded page so it can be summarized.
 *
 * `evaluateJavascript` hands back a JSON-encoded string, so the result has to be
 * unescaped properly — a decoder that only knows `\n`, `\t` and `\"` corrupts
 * every page containing a backslash, a carriage return, or a `\uXXXX` escape.
 */
object PageTextExtractor {
    const val SCRIPT = "document.body.innerText"

    private const val UNICODE_ESCAPE_LENGTH = 4

    fun decode(jsResult: String?): String {
        if (jsResult == null) return ""
        val trimmed = jsResult.trim()
        if (trimmed.isEmpty() || trimmed == "null") return ""
        if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"") || trimmed.length < 2) {
            return trimmed
        }
        return unescape(trimmed.substring(1, trimmed.length - 1)).trim()
    }

    private fun unescape(source: String): String {
        val out = StringBuilder(source.length)
        var index = 0

        while (index < source.length) {
            val char = source[index]
            if (char != '\\') {
                out.append(char)
                index++
                continue
            }

            index++
            if (index >= source.length) break

            when (val escaped = source[index]) {
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'b' -> out.append('\b')
                'f' -> out.append('')
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                '/' -> out.append('/')
                'u' -> {
                    val hex = source.drop(index + 1).take(UNICODE_ESCAPE_LENGTH)
                    val code = hex.takeIf { it.length == UNICODE_ESCAPE_LENGTH }?.toIntOrNull(16)
                    if (code == null) {
                        out.append(escaped)
                    } else {
                        out.append(code.toChar())
                        index += UNICODE_ESCAPE_LENGTH
                    }
                }
                // An escape we do not know means the backslash was literal content.
                else -> out.append(escaped)
            }
            index++
        }

        return out.toString()
    }
}
