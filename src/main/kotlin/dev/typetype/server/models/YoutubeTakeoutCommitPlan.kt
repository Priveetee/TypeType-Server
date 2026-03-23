package dev.typetype.server.models

data class YoutubeTakeoutCommitPlan(
    val importSubscriptions: Boolean,
    val importPlaylists: Boolean,
    val importPlaylistItems: Boolean,
)
