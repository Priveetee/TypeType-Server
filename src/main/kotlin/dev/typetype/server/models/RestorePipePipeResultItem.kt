package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class RestorePipePipeResultItem(
    val history: Int,
    val subscriptions: Int,
    val playlists: Int,
    val playlistVideos: Int,
    val progress: Int,
    val searchHistory: Int,
)
