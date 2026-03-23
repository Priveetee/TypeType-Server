package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutCategoryCounts
import dev.typetype.server.models.YoutubeTakeoutParsedData
import dev.typetype.server.models.YoutubeTakeoutPreviewItem
import dev.typetype.server.models.YoutubeTakeoutPreviewSamples

class YoutubeTakeoutPreviewService(
    private val subscriptionsService: SubscriptionsService,
    private val playlistService: PlaylistService,
    private val lookupService: YoutubeTakeoutPreviewLookupService,
) {
    suspend fun build(userId: String, parsed: YoutubeTakeoutParsedData): YoutubeTakeoutPreviewItem {
        val existingSubs = subscriptionsService.getAll(userId).map { it.channelUrl }.toSet()
        val existingPlaylists = playlistService.getAll(userId).map { it.name.lowercase() }.toSet()
        val existingPlaylistVideos = playlistService.getAll(userId).flatMap { it.videos }.map { it.url }.toSet()
        val existingHistory = lookupService.historyKeys(userId)
        val existingFavorites = lookupService.favorites(userId)
        val existingWatchLater = lookupService.watchLater(userId)
        val counts = YoutubeTakeoutCategoryCounts(
            subscriptions = parsed.subscriptions.size,
            playlists = parsed.playlists.size,
            playlistItems = parsed.playlistItems.values.sumOf { it.size },
            favorites = parsed.favorites.size,
            watchLater = parsed.watchLater.size,
            history = parsed.history.size,
        )
        val dedup = YoutubeTakeoutCategoryCounts(
            subscriptions = parsed.subscriptions.count { it.channelUrl in existingSubs },
            playlists = parsed.playlists.count { it.name.lowercase() in existingPlaylists },
            playlistItems = parsed.playlistItems.values.flatten().count { it.url in existingPlaylistVideos },
            favorites = parsed.favorites.count { it in existingFavorites },
            watchLater = parsed.watchLater.count { it.url in existingWatchLater },
            history = parsed.history.count { (it.url to it.watchedAt) in existingHistory },
        )
        val samples = YoutubeTakeoutPreviewSamples(
            subscriptions = parsed.subscriptions.take(5),
            playlists = parsed.playlists.take(5),
            playlistItems = parsed.playlistItems.values.flatten().take(5),
            favorites = parsed.favorites.take(5),
            watchLater = parsed.watchLater.take(5),
            history = parsed.history.take(5),
        )
        return YoutubeTakeoutPreviewItem(counts = counts, dedup = dedup, samples = samples, warnings = parsed.warnings, errors = parsed.errors)
    }
}
