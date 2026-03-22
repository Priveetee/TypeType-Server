package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutCategoryCounts
import dev.typetype.server.models.YoutubeTakeoutParsedData
import dev.typetype.server.models.YoutubeTakeoutPreviewItem

class YoutubeTakeoutPreviewService(
    private val subscriptionsService: SubscriptionsService,
    private val playlistService: PlaylistService,
) {
    suspend fun build(userId: String, parsed: YoutubeTakeoutParsedData): YoutubeTakeoutPreviewItem {
        val existingSubs = subscriptionsService.getAll(userId).map { it.channelUrl }.toSet()
        val existingPlaylists = playlistService.getAll(userId).map { it.name.lowercase() }.toSet()
        val existingPlaylistVideos = playlistService.getAll(userId).flatMap { it.videos }.map { it.url }.toSet()
        val counts = YoutubeTakeoutCategoryCounts(
            subscriptions = parsed.subscriptions.size,
            playlists = parsed.playlists.size,
            playlistItems = parsed.playlistItems.values.sumOf { it.size },
        )
        val dedup = YoutubeTakeoutCategoryCounts(
            subscriptions = parsed.subscriptions.count { it.channelUrl in existingSubs },
            playlists = parsed.playlists.count { it.name.lowercase() in existingPlaylists },
            playlistItems = parsed.playlistItems.values.flatten().count { it.url in existingPlaylistVideos },
        )
        return YoutubeTakeoutPreviewItem(counts = counts, dedup = dedup, warnings = parsed.warnings, errors = parsed.errors)
    }
}
