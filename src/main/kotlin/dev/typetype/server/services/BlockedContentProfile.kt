package dev.typetype.server.services

import dev.typetype.server.models.BlockedItem
import dev.typetype.server.models.BlockedKeywordItem

data class BlockedContentProfile(
    val videos: List<BlockedItem>,
    val channels: List<BlockedItem>,
    val keywords: List<BlockedKeywordItem>,
) {
    fun allowsVideo(url: String, title: String, uploaderUrl: String, uploaderName: String): Boolean =
        videos.none { normalizeUrl(it.url) == normalizeUrl(url) } &&
            keywords.none { containsBlockedKeyword(title, it.keyword) } &&
            allowsChannel(uploaderUrl, uploaderName)

    fun allowsChannel(url: String, name: String): Boolean = channels.none { item ->
        val blockedUrl = normalizeChannelKey(item.url)
        val blockedName = item.name?.trim().orEmpty()
        blockedUrl.isNotBlank() && blockedUrl == normalizeChannelKey(url) ||
            blockedName.isNotBlank() && blockedName.equals(name.trim(), ignoreCase = true)
    }

    companion object {
        val empty = BlockedContentProfile(videos = emptyList(), channels = emptyList(), keywords = emptyList())
    }
}

private fun normalizeUrl(value: String): String = value.trim().trimEnd('/')
