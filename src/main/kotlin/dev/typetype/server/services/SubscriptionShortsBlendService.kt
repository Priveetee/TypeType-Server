package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem

class SubscriptionShortsBlendService(
    private val trendingService: TrendingService,
    private val signalService: SubscriptionShortsSignalService,
) {
    suspend fun build(
        userId: String,
        subs: List<VideoItem>,
        serviceId: Int,
        page: Int,
        limit: Int,
    ): SubscriptionFeedResponse {
        val discovery = fetchTrendingShorts(serviceId)
            .map { it.toShortCanonicalUrl() }
            .filter { video -> subs.none { it.toShortDedupKey() == video.toShortDedupKey() } }
            .distinctBy { it.url }
        val signals = signalService.load(userId)
        val videos = blend(subs, discovery, limit, signals)
        val hasNext = videos.size >= limit && (subs.size >= limit || discovery.isNotEmpty())
        return SubscriptionFeedResponse(videos = videos, nextpage = if (hasNext) (page + 1).toString() else null)
    }

    private suspend fun fetchTrendingShorts(serviceId: Int): List<VideoItem> =
        when (val trending = trendingService.getTrending(serviceId)) {
            is ExtractionResult.Success -> trending.data
            is ExtractionResult.BadRequest -> emptyList()
            is ExtractionResult.Failure -> emptyList()
        }.filter { it.isShortFormContent || it.duration in 1..60 }

    private fun blend(
        subs: List<VideoItem>,
        discovery: List<VideoItem>,
        limit: Int,
        signalPenaltyByVideo: Map<String, Double>,
    ): List<VideoItem> {
        val rankedSubs = subs.sortedByDescending { scoreShort(it, true, signalPenaltyByVideo) }
        val rankedDiscovery = discovery.sortedByDescending { scoreShort(it, false, signalPenaltyByVideo) }
        val subscriptionUrls = subs.map { it.uploaderUrl }.filter { it.isNotBlank() }.toSet()
        val result = mutableListOf<VideoItem>()
        var si = 0
        var di = 0
        val subQuota = (limit * 0.7).toInt().coerceAtLeast(1)
        while (result.size < limit && (si < rankedSubs.size || di < rankedDiscovery.size)) {
            val currentSubCount = result.count { it.uploaderUrl in subscriptionUrls }
            if (si < rankedSubs.size && currentSubCount < subQuota) result += rankedSubs[si++]
            if (result.size < limit && di < rankedDiscovery.size) result += rankedDiscovery[di++]
            if (si < rankedSubs.size && di >= rankedDiscovery.size && result.size < limit) result += rankedSubs[si++]
        }
        return result.distinctBy { it.url }.take(limit)
    }

    private fun scoreShort(video: VideoItem, isSubscription: Boolean, penaltyByVideo: Map<String, Double>): Double {
        val ageBoost = if (video.uploaded <= 0L) 0.0 else 1.0 / (1.0 + ((System.currentTimeMillis() - video.uploaded).coerceAtLeast(0L) / 3_600_000.0) / 24.0)
        val sourceBoost = if (isSubscription) 1.0 else 0.85
        val penalty = penaltyByVideo[video.url] ?: 1.0
        return (sourceBoost + ageBoost) * penalty
    }
}
