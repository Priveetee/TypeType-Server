package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class NotificationItem(
    val type: String,
    val title: String,
    val createdAt: Long,
    val channelUrl: String,
    val channelName: String,
    val channelAvatarUrl: String,
    val video: VideoItem,
)
