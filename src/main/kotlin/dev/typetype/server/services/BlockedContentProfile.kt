package dev.typetype.server.services

import dev.typetype.server.models.BlockedItem

data class BlockedContentProfile(
    val videos: List<BlockedItem>,
    val channels: List<BlockedItem>,
) {
    fun allowsVideo(url: String, uploaderUrl: String, uploaderName: String): Boolean =
        videos.none { normalizeUrl(it.url) == normalizeUrl(url) } && allowsChannel(uploaderUrl, uploaderName)

    fun allowsChannel(url: String, name: String): Boolean = channels.none { item ->
        val blockedUrl = normalizeChannelKey(item.url)
        val blockedName = item.name?.trim().orEmpty()
        blockedUrl.isNotBlank() && blockedUrl == normalizeChannelKey(url) ||
            blockedName.isNotBlank() && blockedName.equals(name.trim(), ignoreCase = true)
    }

    companion object {
        val empty = BlockedContentProfile(videos = emptyList(), channels = emptyList())
    }
}

private fun normalizeUrl(value: String): String = value.trim().trimEnd('/')
