package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class LikeItem(
    val videoUrl: String,
    val likedAt: Long = 0L,
)
