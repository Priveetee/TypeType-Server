package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.services.BilibiliRelatedService
import dev.typetype.server.services.BilibiliTrendingService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.BugReportService
import dev.typetype.server.services.CachedChannelService
import dev.typetype.server.services.CachedCommentService
import dev.typetype.server.services.CachedManifestService
import dev.typetype.server.services.CachedNativeManifestService
import dev.typetype.server.services.CachedSearchService
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.CachedSuggestionService
import dev.typetype.server.services.CachedTrendingService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.NativeManifestService
import dev.typetype.server.services.NicoNicoTrendingService
import dev.typetype.server.services.NicoVideoProxyService
import dev.typetype.server.services.NotificationsService
import dev.typetype.server.services.OkHttpProxyService
import dev.typetype.server.services.PipePipeBulletCommentService
import dev.typetype.server.services.PipePipeChannelService
import dev.typetype.server.services.PipePipeCommentService
import dev.typetype.server.services.PipePipeSearchService
import dev.typetype.server.services.PipePipeStreamService
import dev.typetype.server.services.PipePipeSuggestionService
import dev.typetype.server.services.PipePipeTrendingService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.ProgressService
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationFeedbackService
import dev.typetype.server.services.RecommendationInterestService
import dev.typetype.server.services.RecommendationPrivacyService
import dev.typetype.server.services.HomeRecommendationSignalContextService
import dev.typetype.server.services.SearchHistoryService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.HomeRecommendationPoolResolverDependencies
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionShortsBlendService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.SubscriptionShortsSignalService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.WatchLaterService
import dev.typetype.server.services.YouTubeSubtitleService
import dev.typetype.server.services.YoutubeTakeoutFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal class ServiceRegistry(cache: DragonflyService, subtitleServiceUrl: String) {
    private val httpClient = OkHttpClient()
    private val proxyHttpClient = httpClient.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followRedirects(true).build()
    val streamService = CachedStreamService(PipePipeStreamService(cache, YouTubeSubtitleService(httpClient, subtitleServiceUrl), BilibiliRelatedService()), cache)
    val searchService = CachedSearchService(PipePipeSearchService(), cache)
    val trendingService = CachedTrendingService(PipePipeTrendingService(BilibiliTrendingService(), NicoNicoTrendingService(httpClient)), cache)
    val commentService = CachedCommentService(PipePipeCommentService(), cache)
    val bulletCommentService = PipePipeBulletCommentService()
    val channelService = CachedChannelService(PipePipeChannelService(), cache)
    val proxyService = OkHttpProxyService(proxyHttpClient)
    val nicoVideoProxyService = NicoVideoProxyService()
    val manifestService = CachedManifestService(ManifestService(streamService), cache)
    val nativeManifestService = CachedNativeManifestService(NativeManifestService(), cache)
    val hlsManifestService = HlsManifestService(streamService, proxyHttpClient)
    val suggestionService = CachedSuggestionService(PipePipeSuggestionService(), cache)
    val recommendationPrivacyService = RecommendationPrivacyService(SettingsService())
    val recommendationInterestService = RecommendationInterestService()
    val recommendationEventService = RecommendationEventService(recommendationInterestService, recommendationPrivacyService)
    val historyService = HistoryService(recommendationEventService)
    val subscriptionsService = SubscriptionsService()
    val subscriptionFeedService = SubscriptionFeedService(subscriptionsService, channelService, cache)
    val subscriptionShortsFeedService = SubscriptionShortsFeedService(
        subscriptionsService,
        channelService,
        SubscriptionShortsBlendService(trendingService, SubscriptionShortsSignalService(recommendationEventService)),
        cache,
    )
    val notificationsService = NotificationsService(subscriptionFeedService)
    val playlistService = PlaylistService()
    val watchLaterService = WatchLaterService(recommendationEventService)
    val progressService = ProgressService()
    val favoritesService = FavoritesService(recommendationEventService)
    val settingsService = SettingsService()
    val searchHistoryService = SearchHistoryService()
    val blockedService = BlockedService(recommendationEventService)
    val bugReportService = BugReportService()
    val recommendationFeedbackService = RecommendationFeedbackService(recommendationEventService)
    val recommendationSignalContextService = HomeRecommendationSignalContextService(subscriptionsService, historyService)
    val youtubeTakeoutImportService = YoutubeTakeoutFactory.create(subscriptionsService, playlistService, historyService, favoritesService, watchLaterService)
    val recommendationPoolResolverDependencies = HomeRecommendationPoolResolverDependencies(
        subscriptionsService = subscriptionsService,
        subscriptionFeedService = subscriptionFeedService,
        historyService = historyService,
        playlistService = playlistService,
        favoritesService = favoritesService,
        watchLaterService = watchLaterService,
        blockedService = blockedService,
        feedbackService = recommendationFeedbackService,
        eventService = recommendationEventService,
        feedHistoryService = dev.typetype.server.services.RecommendationFeedHistoryService(),
        signalContextService = recommendationSignalContextService,
        trendingService = trendingService,
        searchService = searchService,
        cache = cache,
    )
    private val homeRecommendationServices = createHomeRecommendationServices(cache, recommendationPoolResolverDependencies, recommendationPrivacyService)
    val recommendationFeedHistoryService = homeRecommendationServices.feedHistoryService
    val homeRecommendationService = homeRecommendationServices.recommendationService
}
