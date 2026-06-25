package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.WatchLaterTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class UserVideoMetadataRepairService(private val resolver: VideoMetadataResolver) {
    suspend fun repairPlaylists(userId: String): Int = repair(userId, ::playlistCandidateUrls)

    suspend fun repairWatchLater(userId: String): Int = repair(userId, ::watchLaterCandidateUrls)

    suspend fun repairFavorites(userId: String): Int = repair(userId, ::favoriteCandidateUrls)

    private suspend fun repair(userId: String, candidates: suspend (String) -> List<String>): Int {
        val urls = candidates(userId).take(MAX_REPAIR_PER_REQUEST)
        if (urls.isEmpty()) return 0
        val metadata = resolver.resolve(urls)
        if (metadata.isEmpty()) return 0
        return DatabaseFactory.query {
            metadata.values.sumOf { item ->
                updatePlaylistVideos(userId, item) + updateWatchLater(userId, item) + updateFavorites(userId, item)
            }
        }
    }

    private suspend fun playlistCandidateUrls(userId: String): List<String> = DatabaseFactory.query {
        PlaylistVideosTable.selectAll()
            .where { PlaylistVideosTable.userId eq userId }
            .filter { shouldRepair(it[PlaylistVideosTable.title], it[PlaylistVideosTable.thumbnail], it[PlaylistVideosTable.duration], it[PlaylistVideosTable.channelName], it[PlaylistVideosTable.channelUrl]) }
            .map { it[PlaylistVideosTable.url] }
            .distinct()
    }

    private suspend fun watchLaterCandidateUrls(userId: String): List<String> = DatabaseFactory.query {
        WatchLaterTable.selectAll()
            .where { WatchLaterTable.userId eq userId }
            .filter { shouldRepair(it[WatchLaterTable.title], it[WatchLaterTable.thumbnail], it[WatchLaterTable.duration], it[WatchLaterTable.channelName], it[WatchLaterTable.channelUrl]) }
            .map { it[WatchLaterTable.url] }
            .distinct()
    }

    private suspend fun favoriteCandidateUrls(userId: String): List<String> = DatabaseFactory.query {
        FavoritesTable.selectAll()
            .where { FavoritesTable.userId eq userId }
            .filter { shouldRepair(it[FavoritesTable.title], it[FavoritesTable.thumbnail], it[FavoritesTable.duration], it[FavoritesTable.channelName], it[FavoritesTable.channelUrl]) }
            .map { it[FavoritesTable.videoUrl] }
            .distinct()
    }

    private fun shouldRepair(title: String, thumbnail: String, duration: Long, channelName: String, channelUrl: String): Boolean =
        title.startsWith(FALLBACK_TITLE_PREFIX) || thumbnail.startsWith(YOUTUBE_THUMB_PREFIX) ||
            duration <= 0L || channelName.isBlank() || channelUrl.isBlank()

    private fun updatePlaylistVideos(userId: String, item: VideoMetadataItem): Int = PlaylistVideosTable.update({
        (PlaylistVideosTable.userId eq userId) and (PlaylistVideosTable.url eq item.url)
    }) {
        it[title] = item.title; it[thumbnail] = item.thumbnail; it[duration] = item.duration
        it[channelName] = item.channelName; it[channelUrl] = item.channelUrl; it[channelAvatar] = item.channelAvatar
        it[viewCount] = item.viewCount; it[publishedAt] = item.publishedAt
    }

    private fun updateWatchLater(userId: String, item: VideoMetadataItem): Int = WatchLaterTable.update({
        (WatchLaterTable.userId eq userId) and (WatchLaterTable.url eq item.url)
    }) {
        it[title] = item.title; it[thumbnail] = item.thumbnail; it[duration] = item.duration
        it[channelName] = item.channelName; it[channelUrl] = item.channelUrl; it[channelAvatar] = item.channelAvatar
        it[viewCount] = item.viewCount; it[publishedAt] = item.publishedAt
    }

    private fun updateFavorites(userId: String, item: VideoMetadataItem): Int = FavoritesTable.update({
        (FavoritesTable.userId eq userId) and (FavoritesTable.videoUrl eq item.url)
    }) {
        it[title] = item.title; it[thumbnail] = item.thumbnail; it[duration] = item.duration
        it[channelName] = item.channelName; it[channelUrl] = item.channelUrl; it[channelAvatar] = item.channelAvatar
        it[viewCount] = item.viewCount; it[publishedAt] = item.publishedAt
    }

    private companion object {
        const val FALLBACK_TITLE_PREFIX = "YouTube video "
        const val YOUTUBE_THUMB_PREFIX = "https://i.ytimg.com/vi/"
        const val MAX_REPAIR_PER_REQUEST = 25
    }
}
