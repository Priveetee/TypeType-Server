package dev.typetype.server.services

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.YoutubeTakeoutCategoryCounts
import dev.typetype.server.models.YoutubeTakeoutCommitPlan
import dev.typetype.server.models.YoutubeTakeoutImportReportItem
import dev.typetype.server.models.YoutubeTakeoutImportStats
import dev.typetype.server.models.YoutubeTakeoutParsedData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class YoutubeTakeoutImporterService(
    private val subscriptionsService: SubscriptionsService,
    private val playlistService: PlaylistService,
    private val signalImportService: YoutubeTakeoutSignalImportService,
    private val playlistKeyService: YoutubeTakeoutPlaylistKeyService = YoutubeTakeoutPlaylistKeyService(),
) {
    suspend fun commit(userId: String, parsed: YoutubeTakeoutParsedData, plan: YoutubeTakeoutCommitPlan): YoutubeTakeoutImportReportItem = coroutineScope {
        val (issues, issueSummary) = YoutubeTakeoutIssueService.build(parsed.warnings, parsed.errors, stage = "commit")
        val existingSubsDeferred = async { subscriptionsService.getAll(userId).map { it.channelUrl }.toSet() }
        val existingPlaylistsDeferred = async { playlistService.getAll(userId) }
        val sourceMappingsDeferred = async { playlistKeyService.getMappings(userId).toMutableMap() }
        val existingSubs = existingSubsDeferred.await()
        val existingPlaylistRows = existingPlaylistsDeferred.await()
        val existingPlaylists = existingPlaylistRows.associateBy { it.name.lowercase() }
        val sourceMappings = sourceMappingsDeferred.await()
        val existingPlaylistVideos = existingPlaylistRows
            .associateBy({ it.name.lowercase() }, { it.videos.map { v -> v.url }.toSet() })
        var subImported = 0
        var subSkipped = 0
        if (plan.importSubscriptions) {
            parsed.subscriptions.forEach { item ->
                if (item.channelUrl in existingSubs) subSkipped += 1 else {
                    subscriptionsService.add(userId, SubscriptionItem(item.channelUrl, item.name, item.avatarUrl))
                    subImported += 1
                }
            }
        }
        var plImported = 0
        var plSkipped = 0
        var itemImported = 0
        var itemSkipped = 0
        val createdBySource = mutableMapOf<String, PlaylistItem>()
        if (plan.importPlaylists) {
            parsed.playlists.forEach { item ->
                val nameKey = item.name.lowercase()
                val idKey = item.id.lowercase()
                val mappedPlaylistId = sourceMappings[idKey].orEmpty()
                val mapped = if (mappedPlaylistId.isBlank()) null else playlistService.getById(userId, mappedPlaylistId)
                val existing = mapped ?: existingPlaylists[nameKey]
                val playlist = if (existing != null) {
                    plSkipped += 1
                    existing
                } else {
                    val created = playlistService.create(userId, PlaylistItem(name = item.name, description = item.description))
                    plImported += 1
                    created
                }
                createdBySource[nameKey] = playlist
                if (idKey.isNotBlank()) {
                    createdBySource[idKey] = playlist
                    playlistKeyService.putMapping(userId, idKey, playlist.id)
                    sourceMappings[idKey] = playlist.id
                }
            }
        }
        if (plan.importPlaylistItems) {
            parsed.playlistItems.forEach { (playlistKey, videos) ->
                val normalizedKey = playlistKey.lowercase()
                val mappedId = sourceMappings[normalizedKey].orEmpty()
                val mappedPlaylist = if (mappedId.isBlank()) null else playlistService.getById(userId, mappedId)
                val playlist = mappedPlaylist ?: createdBySource[normalizedKey] ?: return@forEach
                val existingUrls = existingPlaylistVideos[playlist.name.lowercase()].orEmpty().toMutableSet()
                videos.forEach { video ->
                    if (video.url in existingUrls) itemSkipped += 1 else {
                        playlistService.addVideo(userId, playlist.id, PlaylistVideoItem(url = video.url, title = video.title, thumbnail = video.thumbnail, duration = video.duration))
                        itemImported += 1
                        existingUrls += video.url
                    }
                }
            }
        }
        val favoriteDeferred = if (plan.importFavorites) async { signalImportService.importFavorites(userId, parsed.favorites) } else null
        val watchLaterDeferred = if (plan.importWatchLater) async { signalImportService.importWatchLater(userId, parsed.watchLater) } else null
        val historyDeferred = if (plan.importHistory) async { signalImportService.importHistory(userId, parsed.history) } else null
        val emptyStats = YoutubeTakeoutImportStats(0, 0, 0)
        val favoriteStats = favoriteDeferred?.await() ?: emptyStats
        val watchLaterStats = watchLaterDeferred?.await() ?: emptyStats
        val historyStats = historyDeferred?.await() ?: emptyStats
        YoutubeTakeoutImportReportItem(
            subscriptions = YoutubeTakeoutImportStats(imported = subImported, skipped = subSkipped, failed = 0),
            playlists = YoutubeTakeoutImportStats(imported = plImported, skipped = plSkipped, failed = 0),
            playlistItems = YoutubeTakeoutImportStats(imported = itemImported, skipped = itemSkipped, failed = 0),
            favorites = favoriteStats,
            watchLater = watchLaterStats,
            history = historyStats,
            skippedItems = YoutubeTakeoutCategoryCounts(subSkipped, plSkipped, itemSkipped, favoriteStats.skipped, watchLaterStats.skipped, historyStats.skipped),
            warnings = parsed.warnings,
            errors = parsed.errors,
            issues = issues,
            issueSummary = issueSummary,
            finishedAt = System.currentTimeMillis(),
        )
    }
}
