package dev.typetype.server.services

import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.ProgressItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll

internal fun playlistProgressByUrl(userId: String, videoUrls: List<String>): Map<String, ProgressItem> {
    val urls = videoUrls.distinct().filter { it.isNotBlank() }
    if (urls.isEmpty()) return emptyMap()
    return ProgressTable.selectAll()
        .where { (ProgressTable.userId eq userId) and (ProgressTable.videoUrl inList urls) }
        .associate {
            it[ProgressTable.videoUrl] to ProgressItem(
                videoUrl = it[ProgressTable.videoUrl],
                position = it[ProgressTable.position],
                updatedAt = it[ProgressTable.updatedAt],
            )
        }
}

internal fun ResultRow.toPlaylistVideoItem(progressByUrl: Map<String, ProgressItem>): PlaylistVideoItem {
    val url = this[PlaylistVideosTable.url]
    val duration = this[PlaylistVideosTable.duration]
    val progress = progressByUrl[url]
    val watchPosition = progress?.position ?: 0L
    return PlaylistVideoItem(
        id = this[PlaylistVideosTable.id],
        url = url,
        title = this[PlaylistVideosTable.title],
        thumbnail = this[PlaylistVideosTable.thumbnail],
        duration = duration,
        position = this[PlaylistVideosTable.position],
        channelName = this[PlaylistVideosTable.channelName],
        channelUrl = this[PlaylistVideosTable.channelUrl],
        channelAvatar = this[PlaylistVideosTable.channelAvatar],
        viewCount = this[PlaylistVideosTable.viewCount],
        watchPosition = watchPosition,
        watched = duration > 0L && watchPosition * 10L >= duration * 9L,
        progressUpdatedAt = progress?.updatedAt ?: 0L,
    )
}
