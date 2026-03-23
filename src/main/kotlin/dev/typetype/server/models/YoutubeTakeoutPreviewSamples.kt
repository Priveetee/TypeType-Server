package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeTakeoutPreviewSamples(
    val subscriptions: List<SubscriptionItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val playlistItems: List<PlaylistVideoItem> = emptyList(),
)
