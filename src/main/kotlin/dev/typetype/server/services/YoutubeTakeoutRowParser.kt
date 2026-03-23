package dev.typetype.server.services

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.SubscriptionItem
import java.text.Normalizer

object YoutubeTakeoutRowParser {
    fun parseSubscription(header: List<String>, row: List<String>): SubscriptionItem? {
        val values = header.zip(row).toMap()
        val channelId = values.pick("channel id", "id des chaines")
        val channelUrl = values.pick("channel url", "url des chaines") ?: channelId?.let { "https://www.youtube.com/channel/$it" }
        val name = values.pick("channel title", "channel name", "title", "name", "titres des chaines") ?: return null
        if (channelUrl.isNullOrBlank()) return null
        return SubscriptionItem(channelUrl = channelUrl, name = name, avatarUrl = values.pick("thumbnail", "avatar") ?: "")
    }

    fun parsePlaylist(header: List<String>, row: List<String>): PlaylistItem? {
        val values = header.zip(row).toMap()
        val title = values.pick("title", "playlist title", "name", "titre d origine de la playlist") ?: return null
        val id = values.pick("playlist id", "playlistid", "id de la playlist") ?: ""
        val description = values.pick("description", "description de la playlist") ?: ""
        return PlaylistItem(id = id, name = title, description = description)
    }

    fun parsePlaylistItem(header: List<String>, row: List<String>): Pair<String, PlaylistVideoItem>? {
        val values = header.zip(row).toMap()
        val playlistKey = values.pick("playlist source key", "playlist id", "playlist title", "playlist") ?: return null
        val videoId = values.pick("video id", "videoid", "id video")
        val videoUrl = values.pick("video url", "url") ?: videoId?.let { "https://www.youtube.com/watch?v=$it" }
        if (videoUrl.isNullOrBlank()) return null
        val title = values.pick("video title", "title", "titre de la video") ?: ""
        val thumbnail = values.pick("thumbnail") ?: ""
        val duration = values.pick("duration")?.toLongOrNull() ?: 0L
        val position = values.pick("position")?.toIntOrNull() ?: 0
        return playlistKey to PlaylistVideoItem(url = videoUrl, title = title, thumbnail = thumbnail, duration = duration, position = position)
    }

    private fun Map<String, String>.pick(vararg keys: String): String? {
        val normalized = entries.associate { normalize(it.key) to it.value.trim() }
        return keys.asSequence().mapNotNull { normalized[it] }.firstOrNull { it.isNotBlank() }
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
