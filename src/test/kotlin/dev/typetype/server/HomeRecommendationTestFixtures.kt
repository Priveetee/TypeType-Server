package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.HomeRecommendationContext
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationPoolResolver
import dev.typetype.server.services.HomeRecommendationPoolResolverDependencies
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.HomeRecommendationSignalContextService
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationFeedHistoryService
import dev.typetype.server.services.RecommendationFeedbackService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionShortsBlendService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.SubscriptionShortsSignalService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import dev.typetype.server.services.WatchLaterService

fun homeResolverDependencies(
    subscriptions: SubscriptionsService,
    channelService: ChannelService,
    cache: CacheService,
    feedbackService: RecommendationFeedbackService,
    eventService: RecommendationEventService,
    feedHistoryService: RecommendationFeedHistoryService,
    trendingService: TrendingService,
    searchService: SearchService,
): HomeRecommendationPoolResolverDependencies = HomeRecommendationPoolResolverDependencies(
    subscriptionsService = subscriptions,
    subscriptionFeedService = SubscriptionFeedService(subscriptions, channelService, cache),
    subscriptionShortsFeedService = SubscriptionShortsFeedService(
        subscriptions,
        channelService,
        SubscriptionShortsBlendService(trendingService, SubscriptionShortsSignalService(eventService)),
        cache,
    ),
    historyService = HistoryService(),
    playlistService = PlaylistService(),
    favoritesService = FavoritesService(),
    watchLaterService = WatchLaterService(),
    blockedService = BlockedService(),
    feedbackService = feedbackService,
    eventService = eventService,
    feedHistoryService = feedHistoryService,
    signalContextService = HomeRecommendationSignalContextService(subscriptions, HistoryService()),
    trendingService = trendingService,
    searchService = searchService,
    cache = cache,
)

fun buildHomeResolver(dependencies: HomeRecommendationPoolResolverDependencies): HomeRecommendationPoolResolver =
    HomeRecommendationPoolResolver(dependencies)

fun defaultContext(serviceId: Int = 0): HomeRecommendationContext = HomeRecommendationContext(
    serviceId = serviceId,
    sessionContext = HomeRecommendationSessionContext(
        intent = HomeRecommendationSessionIntent.AUTO,
        deviceClass = HomeRecommendationDeviceClass.UNKNOWN,
    ),
)
