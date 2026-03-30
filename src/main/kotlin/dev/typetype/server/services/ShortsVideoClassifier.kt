package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

internal object ShortsVideoClassifier {
    fun select(videos: List<VideoItem>): List<VideoItem> {
        val strict = videos.filter(::isStrictShort)
        if (strict.isNotEmpty()) return strict
        return videos.filter(::isFallbackShort)
    }

    private fun isStrictShort(video: VideoItem): Boolean {
        if (video.isShortFormContent) return true
        if ("/shorts/" in video.url.lowercase()) return true
        return false
    }

    private fun isFallbackShort(video: VideoItem): Boolean {
        val duration = video.duration
        if (duration !in 1..180) return false
        val streamType = video.streamType.lowercase()
        if (!streamType.contains("video")) return false
        return hashtagShorts(video) || duration <= 70 || video.title.isNotBlank()
    }

    private fun hashtagShorts(video: VideoItem): Boolean {
        val title = video.title.lowercase()
        val description = video.shortDescription?.lowercase().orEmpty()
        return "#shorts" in title || "#shorts" in description || " shorts" in title
    }
}
