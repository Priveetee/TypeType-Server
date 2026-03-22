package dev.typetype.server.services

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.YoutubeTakeoutImportReportItem
import dev.typetype.server.models.YoutubeTakeoutImportStats
import dev.typetype.server.models.YoutubeTakeoutParsedData

class YoutubeTakeoutImporterService(
    private val subscriptionsService: SubscriptionsService,
    private val playlistService: PlaylistService,
) {
    suspend fun commit(userId: String, parsed: YoutubeTakeoutParsedData): YoutubeTakeoutImportReportItem {
        val existingSubs = subscriptionsService.getAll(userId).map { it.channelUrl }.toSet()
        val existingPlaylists = playlistService.getAll(userId).associateBy { it.name.lowercase() }
        val existingPlaylistVideos = playlistService.getAll(userId)
            .associateBy({ it.name.lowercase() }, { it.videos.map { v -> v.url }.toSet() })
        var subImported = 0
        var subSkipped = 0
        parsed.subscriptions.forEach { item ->
            if (item.channelUrl in existingSubs) subSkipped += 1 else {
                subscriptionsService.add(userId, SubscriptionItem(item.channelUrl, item.name, item.avatarUrl))
                subImported += 1
            }
        }
        var plImported = 0
        var plSkipped = 0
        var itemImported = 0
        var itemSkipped = 0
        val createdByName = mutableMapOf<String, PlaylistItem>()
        parsed.playlists.forEach { item ->
            val key = item.name.lowercase()
            val existing = existingPlaylists[key]
            if (existing != null) {
                plSkipped += 1
                createdByName[key] = existing
            } else {
                val created = playlistService.create(userId, PlaylistItem(name = item.name, description = item.description))
                plImported += 1
                createdByName[key] = created
            }
        }
        parsed.playlistItems.forEach { (playlistKey, videos) ->
            val normalizedKey = playlistKey.lowercase()
            val playlist = createdByName[normalizedKey] ?: createdByName.values.firstOrNull { it.id == playlistKey } ?: return@forEach
            val existingUrls = existingPlaylistVideos[playlist.name.lowercase()].orEmpty().toMutableSet()
            videos.forEach { video ->
                if (video.url in existingUrls) itemSkipped += 1 else {
                    playlistService.addVideo(userId, playlist.id, PlaylistVideoItem(url = video.url, title = video.title, thumbnail = video.thumbnail, duration = video.duration))
                    itemImported += 1
                    existingUrls += video.url
                }
            }
        }
        return YoutubeTakeoutImportReportItem(
            subscriptions = YoutubeTakeoutImportStats(imported = subImported, skipped = subSkipped, failed = 0),
            playlists = YoutubeTakeoutImportStats(imported = plImported, skipped = plSkipped, failed = 0),
            playlistItems = YoutubeTakeoutImportStats(imported = itemImported, skipped = itemSkipped, failed = 0),
            warnings = parsed.warnings,
            errors = parsed.errors,
            finishedAt = System.currentTimeMillis(),
        )
    }
}
