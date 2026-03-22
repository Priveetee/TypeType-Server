package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool

class HomeRecommendationBuilder(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionFeedService: SubscriptionFeedService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
    private val feedbackService: RecommendationFeedbackService,
    private val trendingService: TrendingService,
    private val searchService: SearchService,
) {
    suspend fun build(userId: String, serviceId: Int): HomeRecommendationPool {
        val profile = HomeRecommendationUserSignalService(
            subscriptionsService = subscriptionsService,
            historyService = historyService,
            favoritesService = favoritesService,
            watchLaterService = watchLaterService,
            blockedService = blockedService,
            feedbackSignalService = RecommendationFeedbackSignalService(feedbackService),
            interestProfileService = RecommendationInterestProfileService(),
        ).loadProfile(userId)
        val candidates = HomeRecommendationCandidateService(
            subscriptionFeedService = subscriptionFeedService,
            trendingService = trendingService,
            searchService = searchService,
        )
        val pool = candidates.fetchCandidates(userId = userId, serviceId = serviceId, profile = profile)
        return HomeRecommendationPoolBuilder().build(
            profile = profile,
            subscriptionCandidates = pool.subscriptions,
            discoveryCandidates = pool.discovery,
        )
    }
}
