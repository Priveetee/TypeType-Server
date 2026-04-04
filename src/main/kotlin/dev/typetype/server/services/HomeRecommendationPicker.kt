package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem

class HomeRecommendationPicker(
    private val pool: HomeRecommendationPool,
    private val channelCount: Map<String, Int>,
    private val recentChannels: Set<String>,
    private val recentSemanticKeys: Set<String>,
) {
    fun fromDiscovery(start: Int): Pair<VideoItem?, Int> = pick(pool.discovery, start)

    fun fromSubscriptions(start: Int): Pair<VideoItem?, Int> = pick(pool.subscriptions, start)

    private fun pick(source: List<VideoItem>, start: Int): Pair<VideoItem?, Int> {
        var index = start
        while (index < source.size) {
            val candidate = source[index]
            index += 1
            val channel = channelKey(candidate)
            if (channel.isBlank()) return candidate to index
            val count = channelCount[channel] ?: 0
            if (count >= MAX_PER_CHANNEL_PER_PAGE) continue
            if (channel in recentChannels) continue
            val semanticKey = HomeRecommendationSemanticKey.fromTitle(candidate.title)
            if (semanticKey.isNotBlank() && semanticKey in recentSemanticKeys) continue
            return candidate to index
        }
        return null to index
    }

    private fun channelKey(video: VideoItem): String = video.uploaderUrl.ifBlank { video.uploaderName }

    companion object {
        private const val MAX_PER_CHANNEL_PER_PAGE = 2
    }
}
