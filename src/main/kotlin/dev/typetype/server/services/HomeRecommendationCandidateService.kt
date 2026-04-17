package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

class HomeRecommendationCandidateService(
    private val subscriptionFeedService: SubscriptionFeedService,
    private val subscriptionShortsFeedService: SubscriptionShortsFeedService,
    private val trendingService: TrendingService,
    private val searchService: SearchService,
    private val streamService: StreamService,
    private val discoveryAssembler: HomeRecommendationDiscoveryAssembler = HomeRecommendationDiscoveryAssembler(),
    private val shortsCandidateService: HomeRecommendationShortsCandidateService = HomeRecommendationShortsCandidateService(),
) {
    private val searchCandidateFetcher = HomeRecommendationSearchCandidateFetcher(searchService, trendingService)
    private val relatedCandidateService = HomeRecommendationRelatedCandidateService(streamService)

    suspend fun fetchCandidates(
        userId: String,
        serviceId: Int,
        profile: HomeRecommendationProfile,
        mode: HomeRecommendationPoolMode,
        signalContext: HomeRecommendationSignalContext = HomeRecommendationSignalContext(),
    ): HomeRecommendationCandidatePool {
        if (mode == HomeRecommendationPoolMode.SHORTS) {
            if (serviceId != YOUTUBE_SERVICE_ID) {
                return HomeRecommendationCandidatePool(subscriptions = emptyList(), discovery = emptyList())
            }
            return shortsCandidateService.fetch(userId, serviceId, profile, signalContext, this)
        }
        val subscriptions = fetchSubscriptionCandidates(userId, mode)
            .map { HomeRecommendationTaggedVideo(it, HomeRecommendationSourceTag.SUBSCRIPTION) }
        val subscriptionSeeds = subscriptions.map { it.video.url }
        val relatedFromSubscriptions = relatedCandidateService.fetch(
            seedUrls = subscriptionSeeds,
            source = HomeRecommendationSourceTag.DISCOVERY_THEME,
            seedLimit = HomeRecommendationCandidateLimits.SUBSCRIPTION_SEED_LIMIT,
            relatedPerSeedLimit = HomeRecommendationCandidateLimits.RELATED_PER_SEED_LIMIT,
        )
        val relatedFromFavorites = relatedCandidateService.fetch(
            seedUrls = signalContext.favoriteUrls,
            source = HomeRecommendationSourceTag.DISCOVERY_EXPLORATION,
            seedLimit = HomeRecommendationCandidateLimits.FAVORITE_SEED_LIMIT,
            relatedPerSeedLimit = HomeRecommendationCandidateLimits.RELATED_PER_SEED_LIMIT,
        )
        val discovery = discoveryAssembler.build(
            profile = profile,
            candidates = relatedFromSubscriptions + relatedFromFavorites,
            explorationCap = HomeRecommendationCandidateLimits.RELATED_DISCOVERY_CAP,
        )
        return HomeRecommendationCandidatePool(subscriptions = subscriptions, discovery = discovery)
    }

    suspend fun fetchSubscriptionCandidates(userId: String, mode: HomeRecommendationPoolMode): List<VideoItem> {
        if (mode == HomeRecommendationPoolMode.SHORTS) {
            return HomeRecommendationShortSubscriptionSource.fetch(userId, subscriptionShortsFeedService, subscriptionFeedService)
        }
        if (mode == HomeRecommendationPoolMode.FAST) {
            return subscriptionFeedService.getCachedFeed(
                userId = userId,
                page = 0,
                limit = HomeRecommendationCandidateLimits.FAST_SUBSCRIPTION_PAGE_SIZE,
            )
                ?.videos
                .orEmpty()
        }
        val pages = listOf(
            subscriptionFeedService.getFeed(userId = userId, page = 0, limit = 120).videos,
            subscriptionFeedService.getFeed(userId = userId, page = 1, limit = 120).videos,
        )
        return pages.flatten()
    }

    suspend fun fetchTrendingCandidates(serviceId: Int): List<HomeRecommendationTaggedVideo> =
        searchCandidateFetcher.fetchTrendingCandidates(serviceId)

    suspend fun fetchSearchCandidates(
        serviceId: Int,
        queries: List<String>,
        maxQueries: Int,
        perQueryLimit: Int,
        source: HomeRecommendationSourceTag,
    ): List<HomeRecommendationTaggedVideo> =
        searchCandidateFetcher.fetchSearchCandidates(serviceId, queries, maxQueries, perQueryLimit, source)
}
