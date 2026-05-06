package dev.typetype.server.services

import dev.typetype.server.cache.CacheService

data class HomeRecommendationPoolResolverDependencies(
    val subscriptionsService: SubscriptionsService,
    val subscriptionFeedService: SubscriptionFeedService,
    val subscriptionShortsFeedService: SubscriptionShortsFeedService,
    val historyService: HistoryService,
    val favoritesService: FavoritesService,
    val watchLaterService: WatchLaterService,
    val blockedService: BlockedService,
    val signalContextService: HomeRecommendationSignalContextService,
    val streamService: StreamService = HomeRecommendationNoopStreamService,
    val cache: CacheService,
)
