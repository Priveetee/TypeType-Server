package dev.typetype.server
import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminUserLookupService
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.BugReportService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.NotificationsService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.ProgressService
import dev.typetype.server.services.SavedPlaylistService
import dev.typetype.server.services.SearchHistoryService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.HomeRecommendationPoolResolverDependencies
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionShortsBlendService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.SubscriptionFeedCacheInvalidation
import dev.typetype.server.services.SubscriptionFeedCacheInvalidator
import dev.typetype.server.services.WatchLaterService
import dev.typetype.server.services.YoutubeTakeoutFactory
internal class ServiceRegistry(
    cache: DragonflyService,
    subtitleServiceUrl: String,
    youtubeSessionEncryptionKey: String?,
    adminSettingsService: AdminSettingsService,
) {
    init {
        SubscriptionFeedCacheInvalidation.configure(SubscriptionFeedCacheInvalidator(cache))
    }

    private val extraction = ExtractionServiceRegistry(cache, subtitleServiceUrl, youtubeSessionEncryptionKey)
    val youtubeSessionService = extraction.youtubeSessionService
    val youtubeSessionStreamService = extraction.youtubeSessionStreamService
    val streamService = extraction.streamService
    val searchService = extraction.searchService
    val trendingService = extraction.trendingService
    val commentService = extraction.commentService
    val bulletCommentService = extraction.bulletCommentService
    val channelService = extraction.channelService
    val podcastService = extraction.podcastService
    val publicPlaylistService = extraction.publicPlaylistService
    val proxyService = extraction.proxyService
    val nicoVideoProxyService = extraction.nicoVideoProxyService
    val manifestService = extraction.manifestService
    val nativeManifestService = extraction.nativeManifestService
    val hlsManifestService = extraction.hlsManifestService
    val youtubeSessionHlsManifestService = extraction.youtubeSessionHlsManifestService
    val suggestionService = extraction.suggestionService
    val historyService = HistoryService()
    val subscriptionsService = SubscriptionsService()
    val subscriptionFeedService = SubscriptionFeedService(subscriptionsService, channelService, cache)
    val subscriptionShortsFeedService = SubscriptionShortsFeedService(
        subscriptionsService,
        channelService,
        SubscriptionShortsBlendService(trendingService),
        cache,
    )
    val notificationsService = NotificationsService(subscriptionFeedService)
    val playlistService = PlaylistService()
    val savedPlaylistService = SavedPlaylistService()
    val watchLaterService = WatchLaterService()
    val progressService = ProgressService()
    val favoritesService = FavoritesService()
    val settingsService = SettingsService()
    val searchHistoryService = SearchHistoryService()
    val allowedChannelsService = AllowedChannelsService()
    val allowedPlaylistsService = AllowedPlaylistsService()
    val adminUserLookupService = AdminUserLookupService()
    val accessControlService = AccessControlService(settingsService, allowedChannelsService, allowedPlaylistsService, adminSettingsService)
    val blockedService = BlockedService()
    val bugReportService = BugReportService()
    val youtubeTakeoutImportService = YoutubeTakeoutFactory.create(subscriptionsService, playlistService, historyService, favoritesService, watchLaterService)
    val recommendationPoolResolverDependencies = HomeRecommendationPoolResolverDependencies(
        subscriptionsService = subscriptionsService,
        subscriptionFeedService = subscriptionFeedService,
        subscriptionShortsFeedService = subscriptionShortsFeedService,
        historyService = historyService,
        favoritesService = favoritesService,
        watchLaterService = watchLaterService,
        blockedService = blockedService,
        streamService = streamService,
        cache = cache,
    )
    private val homeRecommendationServices = createHomeRecommendationServices(cache, recommendationPoolResolverDependencies)
    val homeRecommendationService = homeRecommendationServices.recommendationService
    val homeRecommendationWarmupService = homeRecommendationServices.warmupService
}
