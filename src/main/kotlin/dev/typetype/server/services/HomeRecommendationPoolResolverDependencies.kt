package dev.typetype.server.services

import dev.typetype.server.cache.CacheService

data class HomeRecommendationPoolResolverDependencies(
    val subscriptionsService: SubscriptionsService,
    val subscriptionFeedService: SubscriptionFeedService,
    val historyService: HistoryService,
    val playlistService: PlaylistService = PlaylistService(),
    val favoritesService: FavoritesService,
    val watchLaterService: WatchLaterService,
    val blockedService: BlockedService,
    val feedbackService: RecommendationFeedbackService,
    val eventService: RecommendationEventService,
    val feedHistoryService: RecommendationFeedHistoryService,
    val signalContextService: HomeRecommendationSignalContextService,
    val trendingService: TrendingService,
    val searchService: SearchService,
    val cache: CacheService,
)
