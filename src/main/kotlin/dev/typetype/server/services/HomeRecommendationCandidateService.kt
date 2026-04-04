package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem

class HomeRecommendationCandidateService(
    private val subscriptionFeedService: SubscriptionFeedService,
    private val trendingService: TrendingService,
    private val searchService: SearchService,
    private val discoveryAssembler: HomeRecommendationDiscoveryAssembler = HomeRecommendationDiscoveryAssembler(),
) {
    suspend fun fetchCandidates(
        userId: String,
        serviceId: Int,
        profile: HomeRecommendationProfile,
        mode: HomeRecommendationPoolMode,
    ): HomeRecommendationCandidatePool {
        val subscriptions = fetchSubscriptionCandidates(userId, mode)
            .map { HomeRecommendationTaggedVideo(it, HomeRecommendationSourceTag.SUBSCRIPTION) }
        val themeQueryLimit = if (mode == HomeRecommendationPoolMode.FAST) FAST_THEME_QUERY_LIMIT else FULL_THEME_QUERY_LIMIT
        val explorationCap = if (mode == HomeRecommendationPoolMode.FAST) FAST_EXPLORATION_CAP else FULL_EXPLORATION_CAP
        val themedSearchCandidates = if (profile.themeQueries.isEmpty()) {
            emptyList()
        } else {
            fetchSearchCandidates(
                serviceId = serviceId,
                queries = profile.themeQueries,
                maxQueries = themeQueryLimit,
                perQueryLimit = THEME_SEARCH_PER_QUERY,
                source = HomeRecommendationSourceTag.DISCOVERY_THEME,
            )
        }
        val explorationCandidates = fetchSearchCandidates(
            serviceId = serviceId,
            queries = HomeRecommendationExplorationQueryProvider.queries(mode),
            maxQueries = EXPLORATION_QUERY_LIMIT,
            perQueryLimit = EXPLORATION_SEARCH_PER_QUERY,
            source = HomeRecommendationSourceTag.DISCOVERY_EXPLORATION,
        )
        val discovery = discoveryAssembler.build(
            profile = profile,
            candidates = fetchTrendingCandidates(serviceId) + themedSearchCandidates + explorationCandidates,
            explorationCap = explorationCap,
        )
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

    private suspend fun fetchTrendingCandidates(serviceId: Int): List<HomeRecommendationTaggedVideo> =
        when (val trending = trendingService.getTrending(serviceId)) {
            is ExtractionResult.Success -> trending.data.map {
                HomeRecommendationTaggedVideo(it, HomeRecommendationSourceTag.DISCOVERY_TRENDING)
            }
            is ExtractionResult.BadRequest -> emptyList()
            is ExtractionResult.Failure -> emptyList()
        }

    private suspend fun fetchSearchCandidates(
        serviceId: Int,
        queries: List<String>,
        maxQueries: Int,
        perQueryLimit: Int,
        source: HomeRecommendationSourceTag,
    ): List<HomeRecommendationTaggedVideo> {
        val items = mutableListOf<HomeRecommendationTaggedVideo>()
        queries.take(maxQueries).forEach { query ->
            when (val result = searchService.search(query = query, serviceId = serviceId, nextpage = null)) {
                is ExtractionResult.Success -> items += result.data.items
                    .take(perQueryLimit)
                    .map { HomeRecommendationTaggedVideo(it, source) }
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
