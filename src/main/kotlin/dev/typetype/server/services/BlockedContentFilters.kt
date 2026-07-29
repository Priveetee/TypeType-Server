package dev.typetype.server.services

import dev.typetype.server.models.SearchPageResponse

internal fun SearchPageResponse.filterBlocked(profile: BlockedContentProfile): SearchPageResponse = copy(
    items = items.filter { profile.allowsVideo(it.url, it.title, it.uploaderUrl, it.uploaderName) },
    channels = channels.filter { profile.allowsChannel(url = it.url, name = it.name) },
    playlists = playlists.filter { profile.allowsChannel(url = "", name = it.uploaderName) },
)
