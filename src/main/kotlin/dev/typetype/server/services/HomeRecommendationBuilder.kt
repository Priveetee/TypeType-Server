package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool

class HomeRecommendationBuilder(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionFeedService: SubscriptionFeedService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
    private val trendingService: TrendingService,
) {
    suspend fun build(userId: String, serviceId: Int): HomeRecommendationPool {
        val profile = HomeRecommendationUserSignalService(
            subscriptionsService = subscriptionsService,
            historyService = historyService,
            favoritesService = favoritesService,
            watchLaterService = watchLaterService,
            blockedService = blockedService,
        ).loadProfile(userId)
        val candidates = HomeRecommendationCandidateService(
            subscriptionFeedService = subscriptionFeedService,
            trendingService = trendingService,
        )
        val subscriptions = candidates.fetchSubscriptionCandidates(userId)
        val discovery = candidates.fetchDiscoveryCandidates(serviceId)
        return HomeRecommendationPoolBuilder().build(
            profile = profile,
            subscriptionCandidates = subscriptions,
            discoveryCandidates = discovery,
        )
    }
}
