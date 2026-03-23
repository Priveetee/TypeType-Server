package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeTakeoutImportReportItem(
    val subscriptions: YoutubeTakeoutImportStats,
    val playlists: YoutubeTakeoutImportStats,
    val playlistItems: YoutubeTakeoutImportStats,
    val skippedItems: YoutubeTakeoutCategoryCounts = YoutubeTakeoutCategoryCounts(0, 0, 0),
    val warnings: List<String>,
    val errors: List<String>,
    val finishedAt: Long,
)

@Serializable
data class YoutubeTakeoutImportStats(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
)
