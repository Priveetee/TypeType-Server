package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem

class HomeRecommendationCandidateService(
    private val subscriptionFeedService: SubscriptionFeedService,
    private val trendingService: TrendingService,
    private val searchService: SearchService,
) {
    suspend fun fetchCandidates(userId: String, serviceId: Int, profile: HomeRecommendationProfile): HomeRecommendationCandidatePool {
        val subscriptions = fetchSubscriptionCandidates(userId)
        val discovery = (fetchTrendingCandidates(serviceId) + fetchSearchCandidates(serviceId, profile.themeQueries))
            .asSequence()
            .filter { video -> video.uploaderUrl !in profile.subscriptionChannels }
            .filter { video -> video.url !in profile.feedbackBlockedVideos }
            .filter { video -> video.uploaderUrl !in profile.feedbackBlockedChannels }
            .filter { video -> HomeRecommendationLanguageGate.isLikelyPreferred(video, profile) }
            .distinctBy { video -> video.url }
            .toList()
        return HomeRecommendationCandidatePool(subscriptions = subscriptions, discovery = discovery)
    }

    private suspend fun fetchSubscriptionCandidates(userId: String): List<VideoItem> {
        val pages = listOf(
            subscriptionFeedService.getFeed(userId = userId, page = 0, limit = 120).videos,
            subscriptionFeedService.getFeed(userId = userId, page = 1, limit = 120).videos,
        )
        return pages.flatten()
    }

    private suspend fun fetchTrendingCandidates(serviceId: Int): List<VideoItem> =
        when (val trending = trendingService.getTrending(serviceId)) {
            is ExtractionResult.Success -> trending.data
            is ExtractionResult.BadRequest -> emptyList()
            is ExtractionResult.Failure -> emptyList()
        }

    private suspend fun fetchSearchCandidates(serviceId: Int, queries: List<String>): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        queries.take(6).forEach { query ->
            when (val result = searchService.search(query = query, serviceId = serviceId, nextpage = null)) {
                is ExtractionResult.Success -> items += result.data.items.take(18)
                is ExtractionResult.BadRequest -> Unit
                is ExtractionResult.Failure -> Unit
            }
        }
        return items
    }
}
