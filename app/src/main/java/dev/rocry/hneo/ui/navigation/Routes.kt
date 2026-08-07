package dev.rocry.hneo.ui.navigation

import android.net.Uri

/**
 * Every destination, and the one place that knows how its arguments are spelled.
 *
 * Routes carry ids and plain scalars only — never a serialized object — so a
 * destination can be rebuilt from its route alone after process death.
 */
object Routes {
    const val STORIES = "stories"
    const val SETTINGS = "settings"

    const val COMMENTS = "comments/{storyId}"
    const val SUMMARY = "summary/{storyId}"
    const val EXPLAIN = "explain/{selectedText}/{storyTitle}"
    const val WEBVIEW = "webview/{url}"
    const val PAGE_SUMMARY = "page_summary/{url}/{title}"

    const val ARG_STORY_ID = "storyId"
    const val ARG_SELECTED_TEXT = "selectedText"
    const val ARG_STORY_TITLE = "storyTitle"
    const val ARG_URL = "url"
    const val ARG_TITLE = "title"

    fun comments(storyId: Int) = "comments/$storyId"

    fun summary(storyId: Int) = "summary/$storyId"

    fun explain(selectedText: String, storyTitle: String) =
        "explain/${selectedText.asArg()}/${storyTitle.asArg()}"

    fun webView(url: String) = "webview/${url.asArg()}"

    fun pageSummary(url: String, title: String) = "page_summary/${url.asArg()}/${title.asArg()}"

    /** Navigation decodes path segments on the way out, so encode exactly once here. */
    private fun String.asArg(): String = Uri.encode(this)
}
