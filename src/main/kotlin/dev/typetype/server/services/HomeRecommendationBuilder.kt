package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool

class HomeRecommendationBuilder(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionFeedService: SubscriptionFeedService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
    private val eventService: RecommendationEventService,
    private val feedbackService: RecommendationFeedbackService,
    private val trendingService: TrendingService,
    private val searchService: SearchService,
) {
    suspend fun build(userId: String, serviceId: Int, mode: HomeRecommendationPoolMode): HomeRecommendationPool {
        val signalService = HomeRecommendationUserSignalService(
            subscriptionsService = subscriptionsService,
            historyService = historyService,
            favoritesService = favoritesService,
            watchLaterService = watchLaterService,
            blockedService = blockedService,
            recommendationEventService = eventService,
            feedbackSignalService = RecommendationFeedbackSignalService(feedbackService),
            interestProfileService = RecommendationInterestProfileService(),
        )
        val profile = signalService.loadProfile(userId)
        val candidates = HomeRecommendationCandidateService(
            subscriptionFeedService = subscriptionFeedService,
            trendingService = trendingService,
            searchService = searchService,
        )
        val candidatePool = candidates.fetchCandidates(userId = userId, serviceId = serviceId, profile = profile, mode = mode)
        val pool = HomeRecommendationPoolBuilder().build(
            profile = profile,
            subscriptionCandidates = candidatePool.subscriptions,
            discoveryCandidates = candidatePool.discovery,
        )
        val sourceWeights = HomeRecommendationSourceBandit.weightBySource(
            events = eventService.getAll(userId),
            sourceByUrl = pool.sourceByUrl,
        )
        return pool.copy(sourceWeights = sourceWeights)
    }
}
