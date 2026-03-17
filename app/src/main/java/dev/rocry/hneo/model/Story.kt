package dev.rocry.hneo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Story(
    val id: Int,
    val title: String,
    val url: String? = null,
    val points: Int? = null,
    val user: String? = null,
    val time: Int = 0,
    @SerialName("comments_count") val commentsCount: Int = 0,
    val domain: String? = null,
    @SerialName("time_ago") val timeAgo: String = "",
)

@Serializable
data class StoryDetail(
    val id: Int,
    val title: String,
    val url: String? = null,
    val points: Int? = null,
    val user: String? = null,
    val time: Int = 0,
    @SerialName("comments_count") val commentsCount: Int = 0,
    val domain: String? = null,
    @SerialName("time_ago") val timeAgo: String = "",
    val content: String? = null,
    val comments: List<CommentNode> = emptyList(),
)
