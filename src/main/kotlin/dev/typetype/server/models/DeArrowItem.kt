package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class DeArrowItem(
    val videoId: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val attributionUrl: String = "https://dearrow.ajay.app",
)
