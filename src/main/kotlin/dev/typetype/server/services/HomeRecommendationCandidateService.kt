package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem

class HomeRecommendationCandidateService(
    private val subscriptionFeedService: SubscriptionFeedService,
    private val trendingService: TrendingService,
) {
    suspend fun fetchSubscriptionCandidates(userId: String): List<VideoItem> {
        val pages = listOf(
            subscriptionFeedService.getFeed(userId = userId, page = 0, limit = 120).videos,
            subscriptionFeedService.getFeed(userId = userId, page = 1, limit = 120).videos,
        )
        return pages.flatten()
    }

    suspend fun fetchDiscoveryCandidates(serviceId: Int): List<VideoItem> =
        when (val trending = trendingService.getTrending(serviceId)) {
            is ExtractionResult.Success -> trending.data
            is ExtractionResult.BadRequest -> emptyList()
            is ExtractionResult.Failure -> emptyList()
        }
}
