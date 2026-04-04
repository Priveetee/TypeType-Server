package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem

class HomeRecommendationCandidateService(
    private val subscriptionFeedService: SubscriptionFeedService,
    private val trendingService: TrendingService,
    private val searchService: SearchService,
) {
    suspend fun fetchCandidates(
        userId: String,
        serviceId: Int,
        profile: HomeRecommendationProfile,
        mode: HomeRecommendationPoolMode,
    ): HomeRecommendationCandidatePool {
        val subscriptions = fetchSubscriptionCandidates(userId, mode)
        val queryLimit = if (mode == HomeRecommendationPoolMode.FAST) FAST_THEME_QUERY_LIMIT else FULL_THEME_QUERY_LIMIT
        val searchCandidates = if (profile.themeQueries.isEmpty()) {
            emptyList()
        } else {
            fetchSearchCandidates(serviceId, profile.themeQueries, queryLimit)
        }
        val minThemeScore = if (profile.themeTokens.size < 8) 0.24 else 0.34
        val discovery = (fetchTrendingCandidates(serviceId) + searchCandidates)
            .asSequence()
            .filter { video -> video.uploaderUrl !in profile.subscriptionChannels }
            .filter { video -> video.url !in profile.feedbackBlockedVideos }
            .filter { video -> video.uploaderUrl !in profile.feedbackBlockedChannels }
            .filter { video -> HomeRecommendationLanguageGate.isLikelyPreferred(video, profile) }
            .filter { video -> HomeRecommendationLiveTitleDetector.isLiveLike(video.title).not() }
            .filter { video -> (profile.channelInterest[video.uploaderUrl] ?: 0.0) > -1.5 }
            .filter { video ->
                HomeRecommendationThemeExtractor.computeThemeScore(video.title, video.uploaderName, profile.themeTokens) >= minThemeScore
            }
            .distinctBy { video -> video.url }
            .toList()
        return HomeRecommendationCandidatePool(subscriptions = subscriptions, discovery = discovery)
    }

    private suspend fun fetchSubscriptionCandidates(userId: String, mode: HomeRecommendationPoolMode): List<VideoItem> {
        if (mode == HomeRecommendationPoolMode.FAST) {
            return subscriptionFeedService.getCachedFeed(userId = userId, page = 0, limit = FAST_SUBSCRIPTION_PAGE_SIZE)
                ?.videos
                .orEmpty()
        }
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

    private suspend fun fetchSearchCandidates(serviceId: Int, queries: List<String>, maxQueries: Int): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        queries.take(maxQueries).forEach { query ->
            when (val result = searchService.search(query = query, serviceId = serviceId, nextpage = null)) {
                is ExtractionResult.Success -> items += result.data.items.take(18)
                is ExtractionResult.BadRequest -> Unit
                is ExtractionResult.Failure -> Unit
            }
        }
        return items
    }

    companion object {
        private const val FAST_SUBSCRIPTION_PAGE_SIZE = 60
        private const val FAST_THEME_QUERY_LIMIT = 2
        private const val FULL_THEME_QUERY_LIMIT = 6
    }
}
