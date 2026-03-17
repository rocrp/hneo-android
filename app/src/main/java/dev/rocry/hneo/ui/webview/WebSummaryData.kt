package dev.rocry.hneo.ui.webview

/** In-memory holder for passing large webpage content to the summary screen. */
object WebSummaryData {
    var pageTitle: String = ""
    var pageContent: String = ""
    var pageUrl: String = ""

    fun set(title: String, content: String, url: String) {
        pageTitle = title
        pageContent = content
        pageUrl = url
    }

    fun clear() {
        pageTitle = ""
        pageContent = ""
        pageUrl = ""
    }
}
