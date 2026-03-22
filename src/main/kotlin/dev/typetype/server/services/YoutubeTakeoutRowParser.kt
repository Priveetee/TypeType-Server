package dev.typetype.server.services

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.SubscriptionItem

object YoutubeTakeoutRowParser {
    fun parseSubscription(header: List<String>, row: List<String>): SubscriptionItem? {
        val values = header.zip(row).toMap()
        val channelId = values.pick("channel id", "channelid")
        val channelUrl = values.pick("channel url", "channelurl") ?: channelId?.let { "https://www.youtube.com/channel/$it" }
        val name = values.pick("channel title", "channel name", "title", "name") ?: return null
        if (channelUrl.isNullOrBlank()) return null
        return SubscriptionItem(channelUrl = channelUrl, name = name, avatarUrl = values.pick("thumbnail", "avatar") ?: "")
    }

    fun parsePlaylist(header: List<String>, row: List<String>): PlaylistItem? {
        val values = header.zip(row).toMap()
        val title = values.pick("title", "playlist title", "name") ?: return null
        val id = values.pick("playlist id", "playlistid") ?: ""
        val description = values.pick("description") ?: ""
        return PlaylistItem(id = id, name = title, description = description)
    }

    fun parsePlaylistItem(header: List<String>, row: List<String>): Pair<String, PlaylistVideoItem>? {
        val values = header.zip(row).toMap()
        val playlistKey = values.pick("playlist id", "playlist title", "playlist") ?: return null
        val videoId = values.pick("video id", "videoid")
        val videoUrl = values.pick("video url", "url") ?: videoId?.let { "https://www.youtube.com/watch?v=$it" }
        if (videoUrl.isNullOrBlank()) return null
        val title = values.pick("video title", "title") ?: ""
        val thumbnail = values.pick("thumbnail") ?: ""
        val duration = values.pick("duration")?.toLongOrNull() ?: 0L
        val position = values.pick("position")?.toIntOrNull() ?: 0
        return playlistKey to PlaylistVideoItem(url = videoUrl, title = title, thumbnail = thumbnail, duration = duration, position = position)
    }

    private fun Map<String, String>.pick(vararg keys: String): String? {
        val normalized = entries.associate { it.key.trim().lowercase() to it.value.trim() }
        return keys.asSequence().mapNotNull { normalized[it] }.firstOrNull { it.isNotBlank() }
    }
}
