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
        val themeQueryLimit = if (mode == HomeRecommendationPoolMode.FAST) FAST_THEME_QUERY_LIMIT else FULL_THEME_QUERY_LIMIT
        val themedSearchCandidates = if (profile.themeQueries.isEmpty()) {
            emptyList()
        } else {
            fetchSearchCandidates(
                serviceId = serviceId,
                queries = profile.themeQueries,
                maxQueries = themeQueryLimit,
                perQueryLimit = THEME_SEARCH_PER_QUERY,
            )
        }
        val explorationCandidates = fetchSearchCandidates(
            serviceId = serviceId,
            queries = HomeRecommendationExplorationQueryProvider.queries(mode),
            maxQueries = EXPLORATION_QUERY_LIMIT,
            perQueryLimit = EXPLORATION_SEARCH_PER_QUERY,
        )
        val minThemeScore = if (profile.themeTokens.size < 8) 0.24 else 0.34
        val rawDiscovery = (fetchTrendingCandidates(serviceId) + themedSearchCandidates + explorationCandidates)
            .asSequence()
            .filter { video -> video.uploaderUrl !in profile.subscriptionChannels }
            .filter { video -> video.url !in profile.feedbackBlockedVideos }
            .filter { video -> video.uploaderUrl !in profile.feedbackBlockedChannels }
            .filter { video -> HomeRecommendationLiveTitleDetector.isLiveLike(video.title).not() }
            .filter { video -> (profile.channelInterest[video.uploaderUrl] ?: 0.0) > -1.5 }
            .distinctBy { video -> video.url }
            .toList()
        val languagePreferredDiscovery = rawDiscovery.filter { video ->
            HomeRecommendationLanguageGate.isLikelyPreferred(video, profile)
        }
        val thematicDiscovery = languagePreferredDiscovery.filter { video ->
            HomeRecommendationThemeExtractor.computeThemeScore(video.title, video.uploaderName, profile.themeTokens) >= minThemeScore
        }
        val explorationCap = if (mode == HomeRecommendationPoolMode.FAST) FAST_EXPLORATION_CAP else FULL_EXPLORATION_CAP
        val explorationSource = if (languagePreferredDiscovery.isEmpty()) rawDiscovery else languagePreferredDiscovery
        val thematicUrls = thematicDiscovery.map { it.url }.toSet()
        val explorationFill = explorationSource
            .asSequence()
            .filter { video -> video.url !in thematicUrls }
            .take(explorationCap)
            .toList()
        val discovery = (thematicDiscovery + explorationFill).distinctBy { video -> video.url }
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

    private suspend fun fetchSearchCandidates(
        serviceId: Int,
        queries: List<String>,
        maxQueries: Int,
        perQueryLimit: Int,
    ): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        queries.take(maxQueries).forEach { query ->
            when (val result = searchService.search(query = query, serviceId = serviceId, nextpage = null)) {
                is ExtractionResult.Success -> items += result.data.items.take(perQueryLimit)
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
        private const val EXPLORATION_QUERY_LIMIT = 6
        private const val THEME_SEARCH_PER_QUERY = 18
        private const val EXPLORATION_SEARCH_PER_QUERY = 12
        private const val FAST_EXPLORATION_CAP = 24
        private const val FULL_EXPLORATION_CAP = 64
    }
}
