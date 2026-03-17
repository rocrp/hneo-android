package dev.rocry.hneo.model

enum class FeedKind(val endpoint: String, val label: String) {
    TOP("news", "Top"),
    NEW("newest", "New"),
    BEST("best", "Best"),
    ASK("ask", "Ask"),
    SHOW("show", "Show"),
    JOBS("jobs", "Jobs");
}
