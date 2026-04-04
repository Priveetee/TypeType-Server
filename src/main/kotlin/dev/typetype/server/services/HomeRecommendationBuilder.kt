package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool

class HomeRecommendationBuilder(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionFeedService: SubscriptionFeedService,
    private val historyService: HistoryService,
    private val playlistService: PlaylistService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
    private val eventService: RecommendationEventService,
    private val feedbackService: RecommendationFeedbackService,
    private val feedHistoryService: RecommendationFeedHistoryService,
    private val trendingService: TrendingService,
    private val searchService: SearchService,
) {
    suspend fun build(
        userId: String,
        serviceId: Int,
        mode: HomeRecommendationPoolMode,
        personalizationEnabled: Boolean,
        context: HomeRecommendationContext,
    ): HomeRecommendationPool {
        val signalService = HomeRecommendationUserSignalService(
            subscriptionsService = subscriptionsService,
            historyService = historyService,
            playlistService = playlistService,
            favoritesService = favoritesService,
            watchLaterService = watchLaterService,
            blockedService = blockedService,
            recommendationEventService = eventService,
            feedHistoryService = feedHistoryService,
            feedbackSignalService = RecommendationFeedbackSignalService(feedbackService),
            interestProfileService = RecommendationInterestProfileService(),
        )
        val profile = signalService.loadProfile(
            userId = userId,
            personalizationEnabled = personalizationEnabled,
            sessionContext = context.sessionContext,
        )
        val allEvents = if (!personalizationEnabled) emptyList() else eventService.getAll(userId)
        val personaState = HomeRecommendationPersonaTracker.infer(
            events = allEvents,
            sessionContext = context.sessionContext,
        )
        val effectiveContext = when (personaState.persona) {
            HomeRecommendationSessionPersona.AUTO -> context.sessionContext
            HomeRecommendationSessionPersona.QUICK -> context.sessionContext.copy(intent = HomeRecommendationSessionIntent.QUICK)
            HomeRecommendationSessionPersona.DEEP -> context.sessionContext.copy(intent = HomeRecommendationSessionIntent.DEEP)
        }
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
            context = effectiveContext,
            mode = mode,
        )
        val sourceWeights = if (!personalizationEnabled) {
            emptyMap()
        } else {
            val classic = HomeRecommendationSourceBandit.weightBySource(
                events = allEvents,
                sourceByUrl = pool.sourceByUrl,
            )
            val contextual = HomeRecommendationContextualBandit.weightBySource(
                events = allEvents,
                sourceByUrl = pool.sourceByUrl,
                serviceId = serviceId,
                sessionIntent = effectiveContext.intent,
                deviceClass = effectiveContext.deviceClass,
            )
            (classic.keys + contextual.keys).associateWith { source ->
                val a = classic[source] ?: 1.0
                val b = contextual[source] ?: 1.0
                ((a + b) / 2.0).coerceIn(0.45, 1.55)
            }
        }
        val mergedWeights = (pool.sourceWeights.keys + sourceWeights.keys).associateWith { source ->
            val poolWeight = pool.sourceWeights[source] ?: 1.0
            val banditWeight = sourceWeights[source] ?: 1.0
            ((poolWeight + banditWeight) / 2.0).coerceIn(0.55, 1.55)
        }
        val explorationWeights = if (!personalizationEnabled) mergedWeights else {
            HomeRecommendationExplorationBandit.apply(
                events = allEvents,
                sourceWeights = mergedWeights,
                sourceByUrl = pool.sourceByUrl,
            )
        }
        return pool.copy(sourceWeights = explorationWeights)
    }
}
