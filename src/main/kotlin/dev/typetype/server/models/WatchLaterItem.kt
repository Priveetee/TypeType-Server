package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class WatchLaterItem(
    val url: String,
    val title: String,
    val thumbnail: String,
    val duration: Long,
    val addedAt: Long = 0L,
)
