package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeTakeoutPreviewItem(
    val counts: YoutubeTakeoutCategoryCounts,
    val dedup: YoutubeTakeoutCategoryCounts,
    val warnings: List<String>,
    val errors: List<String>,
)

@Serializable
data class YoutubeTakeoutCategoryCounts(
    val subscriptions: Int,
    val playlists: Int,
    val playlistItems: Int,
)
