package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem

class SubscriptionShortsBlendService(private val trendingService: TrendingService) {
    suspend fun build(
        subs: List<VideoItem>,
        serviceId: Int,
        page: Int,
        limit: Int,
    ): SubscriptionFeedResponse {
        val discovery = fetchTrendingShorts(serviceId)
            .map { it.toShortCanonicalUrl() }
            .filter { video -> subs.none { it.toShortDedupKey() == video.toShortDedupKey() } }
            .distinctBy { it.url }
        val videos = blend(subs, discovery, limit)
        val hasNext = videos.size >= limit && (subs.size >= limit || discovery.isNotEmpty())
        return SubscriptionFeedResponse(videos = videos, nextpage = if (hasNext) (page + 1).toString() else null)
    }

    private suspend fun fetchTrendingShorts(serviceId: Int): List<VideoItem> =
        when (val trending = trendingService.getTrending(serviceId)) {
            is ExtractionResult.Success -> trending.data
            is ExtractionResult.BadRequest -> emptyList()
            is ExtractionResult.Failure -> emptyList()
        }.filter { it.isShortFormContent || it.duration in 1..60 }

    private fun blend(subs: List<VideoItem>, discovery: List<VideoItem>, limit: Int): List<VideoItem> {
        val subscriptionUrls = subs.map { it.uploaderUrl }.filter { it.isNotBlank() }.toSet()
        val result = mutableListOf<VideoItem>()
        var si = 0
        var di = 0
        val subQuota = (limit * 0.7).toInt().coerceAtLeast(1)
        while (result.size < limit && (si < subs.size || di < discovery.size)) {
            val currentSubCount = result.count { it.uploaderUrl in subscriptionUrls }
            if (si < subs.size && currentSubCount < subQuota) result += subs[si++]
            if (result.size < limit && di < discovery.size) result += discovery[di++]
            if (si < subs.size && di >= discovery.size && result.size < limit) result += subs[si++]
        }
        return result.distinctBy { it.url }.take(limit)
    }
}
