package dev.rocry.hneo.data.llm

import dev.rocry.hneo.data.AppSettings

/** The prompt for a request shape lives here and nowhere else. */
internal fun LlmRequest.systemPrompt(settings: AppSettings): String = when (this) {
    is LlmRequest.SummarizeStory -> settings.llmSystemPrompt
    is LlmRequest.SummarizePage -> settings.llmWebpageSummaryPrompt
    is LlmRequest.Explain -> settings.llmExplainPrompt
}

internal fun LlmRequest.userPrompt(settings: AppSettings): String = when (this) {
    is LlmRequest.SummarizeStory -> buildString {
        appendLine("[Story] ${story.title}")
        story.url?.let { appendLine("[URL] $it") }
        appendLine("[Score] ${story.points ?: 0} | [Comments] ${story.commentsCount}")
        appendLine()
        for (comment in comments.take(settings.llmMaxComments)) {
            appendLine("${"  ".repeat(comment.depth)}[${comment.user}] ${comment.text}")
        }
    }

    is LlmRequest.SummarizePage -> buildString {
        appendLine("# $title")
        if (url.isNotBlank()) appendLine("URL: $url")
        appendLine()
        appendLine("## Page Content")
        appendLine()
        append(content.take(MAX_PAGE_CHARS))
    }

    is LlmRequest.Explain -> buildString {
        appendLine("Story: $storyTitle")
        appendLine("Selected text: $selectedText")
    }
}

private const val MAX_PAGE_CHARS = 12_000
