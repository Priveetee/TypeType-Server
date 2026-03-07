package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistVideoItem(
    val id: String = "",
    val url: String,
    val title: String,
    val thumbnail: String,
    val duration: Long,
    val position: Int = 0,
)
