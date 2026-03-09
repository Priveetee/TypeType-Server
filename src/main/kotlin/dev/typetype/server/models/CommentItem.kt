package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class CommentItem(
    val id: String,
    val text: String,
    val author: String,
    val authorUrl: String,
    val authorAvatarUrl: String,
    val likeCount: Long,
    val publishedTime: String,
    val isHeartedByUploader: Boolean,
    val isPinned: Boolean,
    val replyCount: Int,
    val repliesPage: String?,
)
