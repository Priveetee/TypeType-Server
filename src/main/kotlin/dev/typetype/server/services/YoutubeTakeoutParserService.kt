package dev.typetype.server.services

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.YoutubeTakeoutParsedData
import java.nio.file.Path
import java.util.zip.ZipFile

class YoutubeTakeoutParserService {
    fun parse(zipPath: Path): YoutubeTakeoutParsedData {
        val scan = YoutubeTakeoutZipScanner.scan(zipPath)
        val warnings = scan.warnings.toMutableList()
        val errors = mutableListOf<String>()
        val subscriptions = scan.subscriptionsRows.mapNotNull { row ->
            runCatching { YoutubeTakeoutRowParser.parseSubscription(scan.subscriptionsHeader, row) }.getOrElse {
                errors += "Invalid subscription row"
                null
            }
        }
        val playlists = scan.playlistsRows.mapNotNull { row ->
            runCatching { YoutubeTakeoutRowParser.parsePlaylist(scan.playlistsHeader, row) }.getOrElse {
                errors += "Invalid playlist row"
                null
            }
        }
        val playlistItems = mutableMapOf<String, MutableList<PlaylistVideoItem>>()
        scan.playlistItemsRows.forEach { row ->
            val parsed = runCatching { YoutubeTakeoutRowParser.parsePlaylistItem(scan.playlistItemsHeader, row) }.getOrNull()
            if (parsed == null) {
                errors += "Invalid playlist item row"
            } else {
                playlistItems.getOrPut(parsed.first) { mutableListOf() }.add(parsed.second)
            }
        }
        val history = parseHistory(zipPath, warnings)
        val watchLater = playlistItems.filterKeys { isWatchLaterPlaylistKey(it) }.values.flatten()
        val favorites = playlistItems.filterKeys { isLikedPlaylistKey(it) }.values.flatten().map { it.url }
        val activitySignals = YoutubeTakeoutActivitySignalService.parse(zipPath)
        val mergedSubscriptions = dedupSubscriptions(subscriptions + activitySignals.first)
        val mergedFavorites = (favorites + activitySignals.second).distinct()
        if (subscriptions.isEmpty()) warnings += "No subscription rows detected"
        return YoutubeTakeoutParsedData(
            subscriptions = mergedSubscriptions,
            playlists = dedupPlaylists(playlists),
            playlistItems = dedupPlaylistItems(playlistItems),
            favorites = mergedFavorites,
            watchLater = watchLater.distinctBy { it.url },
            history = dedupHistory(history),
            warnings = warnings,
            errors = errors,
        )
    }

    private fun dedupSubscriptions(items: List<SubscriptionItem>): List<SubscriptionItem> = items.distinctBy { it.channelUrl }

    private fun dedupPlaylists(items: List<PlaylistItem>): List<PlaylistItem> = items.distinctBy { it.id.ifBlank { it.name } }

    private fun dedupPlaylistItems(map: Map<String, List<PlaylistVideoItem>>): Map<String, List<PlaylistVideoItem>> =
        map.mapValues { (_, items) -> items.distinctBy { it.url } }

    private fun dedupHistory(items: List<HistoryItem>): List<HistoryItem> = items.distinctBy { it.url to it.watchedAt }

    private fun parseHistory(zipPath: Path, warnings: MutableList<String>): List<HistoryItem> {
        ZipFile(zipPath.toFile()).use { zip ->
            val entries = zip.entries().asSequence().filter { item ->
                val normalized = item.name.lowercase()
                !item.isDirectory && normalized.endsWith(".html") && normalized.contains("youtube")
            }.toList()
            val entry = entries.firstOrNull { it.name.lowercase().contains("watch-history") }
                ?: entries.firstOrNull { it.name.lowercase().contains("monactiv") }
                ?: entries.firstOrNull()
            if (entry == null) return emptyList()
            val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val parsed = YoutubeTakeoutHistoryParser.parse(html)
            if (parsed.isEmpty()) warnings += "No watch history rows detected"
            return parsed
        }
    }

    private fun isLikedPlaylistKey(value: String): Boolean {
        val key = value.lowercase()
        return key == "liked videos" || key == "videos que j aime" || key == "vid eos que j aime" || key == "j aime"
    }

    private fun isWatchLaterPlaylistKey(value: String): Boolean {
        val key = value.lowercase()
        return key == "watch later" || key == "a regarder plus tard"
    }
}
