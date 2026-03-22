package dev.typetype.server.services

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.YoutubeTakeoutParsedData
import java.nio.file.Path

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
        if (subscriptions.isEmpty()) warnings += "No subscription rows detected"
        return YoutubeTakeoutParsedData(
            subscriptions = dedupSubscriptions(subscriptions),
            playlists = dedupPlaylists(playlists),
            playlistItems = dedupPlaylistItems(playlistItems),
            warnings = warnings,
            errors = errors,
        )
    }

    private fun dedupSubscriptions(items: List<SubscriptionItem>): List<SubscriptionItem> = items.distinctBy { it.channelUrl }

    private fun dedupPlaylists(items: List<PlaylistItem>): List<PlaylistItem> = items.distinctBy { it.id.ifBlank { it.name } }

    private fun dedupPlaylistItems(map: Map<String, List<PlaylistVideoItem>>): Map<String, List<PlaylistVideoItem>> =
        map.mapValues { (_, items) -> items.distinctBy { it.url } }
}
