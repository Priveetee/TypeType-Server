package dev.typetype.server.services

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.models.YoutubeTakeoutImportStats

class YoutubeTakeoutSignalImportService(
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val historyService: HistoryService,
) {
    suspend fun importFavorites(userId: String, urls: List<String>): YoutubeTakeoutImportStats {
        var imported = 0
        var skipped = 0
        val existing = favoritesService.getAll(userId).map { it.videoUrl }.toMutableSet()
        urls.forEach { url ->
            if (url in existing) skipped += 1 else {
                favoritesService.add(userId, url)
                imported += 1
                existing += url
            }
        }
        return YoutubeTakeoutImportStats(imported = imported, skipped = skipped, failed = 0)
    }

    suspend fun importWatchLater(userId: String, videos: List<PlaylistVideoItem>): YoutubeTakeoutImportStats {
        var imported = 0
        var skipped = 0
        val existing = watchLaterService.getAll(userId).map { it.url }.toMutableSet()
        videos.forEach { video ->
            if (video.url in existing) skipped += 1 else {
                watchLaterService.add(userId, WatchLaterItem(url = video.url, title = video.title, thumbnail = video.thumbnail, duration = video.duration))
                imported += 1
                existing += video.url
            }
        }
        return YoutubeTakeoutImportStats(imported = imported, skipped = skipped, failed = 0)
    }

    suspend fun importHistory(userId: String, items: List<HistoryItem>): YoutubeTakeoutImportStats {
        var skipped = 0
        val existing = historyService.dedupKeys(userId).toMutableSet()
        val toInsert = mutableListOf<HistoryItem>()
        items.forEach { item ->
            val key = item.url to item.watchedAt
            if (key in existing) skipped += 1 else {
                toInsert += item
                existing += key
            }
        }
        val imported = historyService.addImportedBatch(userId, toInsert)
        return YoutubeTakeoutImportStats(imported = imported, skipped = skipped, failed = 0)
    }
}
