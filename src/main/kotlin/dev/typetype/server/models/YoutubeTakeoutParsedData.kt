package dev.typetype.server.models

data class YoutubeTakeoutParsedData(
    val subscriptions: List<SubscriptionItem>,
    val playlists: List<PlaylistItem>,
    val playlistItems: Map<String, List<PlaylistVideoItem>>,
    val warnings: List<String>,
    val errors: List<String>,
)
