package dev.rocry.hneo.model

import kotlinx.serialization.Serializable

@Serializable
data class CommentNode(
    val id: Int = 0,
    val user: String? = null,
    @kotlinx.serialization.SerialName("time_ago") val timeAgo: String? = null,
    val content: String? = null,
    val comments: List<CommentNode> = emptyList(),
    val level: Int = 0,
    val deleted: Boolean? = null,
    val dead: Boolean? = null,
)

data class FlatComment(
    val id: Int,
    val user: String,
    val timeAgo: String,
    val text: String,
    val depth: Int,
    val childCount: Int,
)

fun flattenComments(nodes: List<CommentNode>, depth: Int = 0): List<FlatComment> {
    val result = mutableListOf<FlatComment>()
    for (node in nodes) {
        if (node.deleted == true || node.dead == true) continue
        val text = node.content?.stripHtml() ?: ""
        if (text.isBlank() && node.comments.isEmpty()) continue
        val childFlat = flattenComments(node.comments, depth + 1)
        result += FlatComment(
            id = node.id,
            user = node.user ?: "[deleted]",
            timeAgo = node.timeAgo ?: "",
            text = text,
            depth = depth,
            childCount = childFlat.size,
        )
        result += childFlat
    }
    return result
}

fun String.stripHtml(): String {
    var s = this
    s = s.replace(Regex("<br\\s*/?>"), "\n")
    s = s.replace("</p><p>", "\n\n")
    s = s.replace(Regex("<pre><code>"), "\n```\n")
    s = s.replace(Regex("</code></pre>"), "\n```\n")
    s = s.replace(Regex("<[^>]*>"), "")
    s = s.replace("&amp;", "&")
    s = s.replace("&lt;", "<")
    s = s.replace("&gt;", ">")
    s = s.replace("&quot;", "\"")
    s = s.replace("&#x27;", "'")
    s = s.replace("&#39;", "'")
    s = s.replace("&#x2F;", "/")
    s = s.replace(Regex("&#(\\d+);")) { chr ->
        chr.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: chr.value
    }
    s = s.replace(Regex("&#x([0-9a-fA-F]+);")) { chr ->
        chr.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: chr.value
    }
    s = s.replace(Regex("\n{3,}"), "\n\n")
    return s.trim()
}
