package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.services.BlockedService
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
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.NativeManifestService
import dev.typetype.server.services.NicoVideoProxyService
import dev.typetype.server.services.OkHttpProxyService
import dev.typetype.server.services.PipePipeBulletCommentService
import dev.typetype.server.services.PipePipeChannelService
import dev.typetype.server.services.PipePipeCommentService
import dev.typetype.server.services.PipePipeSearchService
import dev.typetype.server.services.PipePipeStreamService
import dev.typetype.server.services.YouTubeSubtitleService
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import dev.typetype.server.services.PipePipeSuggestionService
import dev.typetype.server.services.BilibiliRelatedService
import dev.typetype.server.services.BilibiliTrendingService
import dev.typetype.server.services.NicoNicoTrendingService
import dev.typetype.server.services.PipePipeTrendingService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.ProgressService
import dev.typetype.server.services.SearchHistoryService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.WatchLaterService

internal class ServiceRegistry(cache: DragonflyService, subtitleServiceUrl: String) {
    private val httpClient = OkHttpClient()
    private val proxyHttpClient = httpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    val streamService = CachedStreamService(PipePipeStreamService(cache, YouTubeSubtitleService(httpClient, subtitleServiceUrl), BilibiliRelatedService()), cache)
    val searchService = CachedSearchService(PipePipeSearchService(), cache)
    val trendingService = CachedTrendingService(
        PipePipeTrendingService(BilibiliTrendingService(), NicoNicoTrendingService(httpClient)),
        cache,
    )
    val commentService = CachedCommentService(PipePipeCommentService(), cache)
    val bulletCommentService = PipePipeBulletCommentService()
    val channelService = CachedChannelService(PipePipeChannelService(), cache)
    val proxyService = OkHttpProxyService(proxyHttpClient)
    val nicoVideoProxyService = NicoVideoProxyService()
    val manifestService = CachedManifestService(ManifestService(streamService), cache)
    val nativeManifestService = CachedNativeManifestService(NativeManifestService(), cache)
    val hlsManifestService = HlsManifestService(streamService, proxyHttpClient)
    val suggestionService = CachedSuggestionService(PipePipeSuggestionService(), cache)
    val historyService = HistoryService()
    val subscriptionsService = SubscriptionsService()
    val subscriptionFeedService = SubscriptionFeedService(subscriptionsService, channelService, cache)
    val playlistService = PlaylistService()
    val watchLaterService = WatchLaterService()
    val progressService = ProgressService()
    val favoritesService = FavoritesService()
    val settingsService = SettingsService()
    val searchHistoryService = SearchHistoryService()
    val blockedService = BlockedService()
    val homeRecommendationService = HomeRecommendationService(
        subscriptionsService = subscriptionsService,
        subscriptionFeedService = subscriptionFeedService,
        historyService = historyService,
        favoritesService = favoritesService,
        watchLaterService = watchLaterService,
        blockedService = blockedService,
        trendingService = trendingService,
        cache = cache,
    )
}
