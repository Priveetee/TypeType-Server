package dev.typetype.server.services

import dev.typetype.server.models.PodcastItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem

internal fun PlaylistInfoItem.toPodcastItem(): PodcastItem = PodcastItem(
    id = url ?: "",
    title = name ?: "",
    url = url ?: "",
    thumbnailUrl = thumbnailUrl.toAbsoluteUrl(),
    uploaderName = uploaderName ?: "",
    streamCount = streamCount,
    playlistType = playlistType?.name?.lowercase() ?: "",
)

internal fun PlaylistInfo.toPodcastItem(): PodcastItem = PodcastItem(
    id = url ?: "",
    title = name ?: "",
    url = url ?: "",
    thumbnailUrl = thumbnailUrl.toAbsoluteUrl(),
    uploaderName = uploaderName ?: "",
    streamCount = streamCount,
    playlistType = playlistType?.name?.lowercase() ?: "",
)

internal fun emptyPodcastItem(url: String): PodcastItem = PodcastItem(
    id = url,
    title = "",
    url = url,
    thumbnailUrl = "",
    uploaderName = "",
    streamCount = -1L,
    playlistType = "",
)
