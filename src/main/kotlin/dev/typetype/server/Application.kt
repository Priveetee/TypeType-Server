package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.downloader.OkHttpDownloader
import dev.typetype.server.routes.blockedRoutes
import dev.typetype.server.routes.manifestRoutes
import dev.typetype.server.routes.channelRoutes
import dev.typetype.server.routes.commentRoutes
import dev.typetype.server.routes.historyRoutes
import dev.typetype.server.routes.favoritesRoutes
import dev.typetype.server.routes.playlistRoutes
import dev.typetype.server.routes.progressRoutes
import dev.typetype.server.routes.proxyRoutes
import dev.typetype.server.routes.searchHistoryRoutes
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.routes.suggestionRoutes
import dev.typetype.server.routes.settingsRoutes
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.routes.subscriptionsRoutes
import dev.typetype.server.routes.tokenRoutes
import dev.typetype.server.routes.trendingRoutes
import dev.typetype.server.routes.watchLaterRoutes
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.CachedChannelService
import dev.typetype.server.services.CachedCommentService
import dev.typetype.server.services.CachedSearchService
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.CachedTrendingService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.NativeManifestService
import dev.typetype.server.services.OkHttpProxyService
import dev.typetype.server.services.PipePipeChannelService
import dev.typetype.server.services.PipePipeCommentService
import dev.typetype.server.services.PipePipeSearchService
import dev.typetype.server.services.PipePipeStreamService
import dev.typetype.server.services.PipePipeSuggestionService
import dev.typetype.server.services.PipePipeTrendingService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.ProgressService
import dev.typetype.server.services.SearchHistoryService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TokenService
import dev.typetype.server.services.WatchLaterService
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import org.schabi.newpipe.extractor.NewPipe
import org.slf4j.LoggerFactory

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val log = LoggerFactory.getLogger("Application")

    NewPipe.init(OkHttpDownloader.instance())
    launchExtractorLifecycle()

    val dbUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/typetype"
    val dbUser = System.getenv("DATABASE_USER") ?: "typetype"
    val dbPassword = System.getenv("DATABASE_PASSWORD") ?: "typetype"
    DatabaseFactory.init(dbUrl, dbUser, dbPassword)

    val token = TokenService().getOrGenerate()
    log.info("Instance token: $token")

    val cacheUrl = System.getenv("DRAGONFLY_URL") ?: "redis://localhost:6379"
    val cache = DragonflyService(cacheUrl)

    val streamService = CachedStreamService(PipePipeStreamService(cache), cache)
    val searchService = CachedSearchService(PipePipeSearchService(), cache)
    val trendingService = CachedTrendingService(PipePipeTrendingService(), cache)
    val commentService = CachedCommentService(PipePipeCommentService(), cache)
    val channelService = CachedChannelService(PipePipeChannelService(), cache)
    val proxyService = OkHttpProxyService()
    val manifestService = ManifestService(streamService)
    val nativeManifestService = NativeManifestService()
    val suggestionService = PipePipeSuggestionService()

    val historyService = HistoryService()
    val subscriptionsService = SubscriptionsService()
    val playlistService = PlaylistService()
    val watchLaterService = WatchLaterService()
    val progressService = ProgressService()
    val favoritesService = FavoritesService()
    val settingsService = SettingsService()
    val searchHistoryService = SearchHistoryService()
    val blockedService = BlockedService()
    val tokenService = TokenService()

    configurePlugins()

    routing {
        streamRoutes(streamService)
        manifestRoutes(manifestService, nativeManifestService)
        searchRoutes(searchService)
        suggestionRoutes(suggestionService)
        trendingRoutes(trendingService)
        commentRoutes(commentService)
        channelRoutes(channelService)
        proxyRoutes(proxyService)
        tokenRoutes(tokenService)
        historyRoutes(historyService, token)
        subscriptionsRoutes(subscriptionsService, token)
        playlistRoutes(playlistService, token)
        watchLaterRoutes(watchLaterService, token)
        progressRoutes(progressService, token)
        favoritesRoutes(favoritesService, token)
        settingsRoutes(settingsService, token)
        searchHistoryRoutes(searchHistoryService, token)
        blockedRoutes(blockedService, token)
    }
}
