package dev.typetype.server.services

import dev.typetype.server.models.PlaylistResultItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem

internal fun PlaylistInfoItem.toPlaylistResultItem(): PlaylistResultItem = PlaylistResultItem(
    id = url ?: "",
    title = name ?: "",
    url = url ?: "",
    thumbnailUrl = thumbnailUrl.toAbsoluteUrl(),
    uploaderName = uploaderName ?: "",
    streamCount = streamCount,
    playlistType = playlistType?.name?.lowercase() ?: "",
)
