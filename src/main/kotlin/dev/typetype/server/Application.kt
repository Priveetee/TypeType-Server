package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.downloader.OkHttpDownloader
import dev.typetype.server.routes.blockedRoutes
import dev.typetype.server.routes.bulletCommentRoutes
import dev.typetype.server.routes.channelRoutes
import dev.typetype.server.routes.commentRoutes
import dev.typetype.server.routes.favoritesRoutes
import dev.typetype.server.routes.historyRoutes
import dev.typetype.server.routes.manifestRoutes
import dev.typetype.server.routes.nicoVideoProxyRoutes
import dev.typetype.server.routes.playlistRoutes
import dev.typetype.server.routes.progressRoutes
import dev.typetype.server.routes.proxyRoutes
import dev.typetype.server.routes.searchHistoryRoutes
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.routes.settingsRoutes
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.routes.subscriptionFeedRoutes
import dev.typetype.server.routes.subscriptionsRoutes
import dev.typetype.server.routes.suggestionRoutes
import dev.typetype.server.routes.tokenRoutes
import dev.typetype.server.routes.trendingRoutes
import dev.typetype.server.routes.watchLaterRoutes
import dev.typetype.server.services.TokenService
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing
import org.schabi.newpipe.extractor.NewPipe

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    NewPipe.init(OkHttpDownloader.instance())
    launchExtractorLifecycle()

    val dbUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/typetype"
    val dbUser = System.getenv("DATABASE_USER") ?: "typetype"
    val dbPassword = System.getenv("DATABASE_PASSWORD") ?: "typetype"
    DatabaseFactory.init(dbUrl, dbUser, dbPassword)

    val tokenService = TokenService()

    val cacheUrl = System.getenv("DRAGONFLY_URL") ?: "redis://localhost:6379"
    val svc = ServiceRegistry(DragonflyService(cacheUrl))

    configurePlugins()

    routing {
        rateLimit(EXTRACTION_ZONE) {
            streamRoutes(svc.streamService)
            manifestRoutes(svc.manifestService, svc.nativeManifestService)
            searchRoutes(svc.searchService)
            suggestionRoutes(svc.suggestionService)
            trendingRoutes(svc.trendingService)
            commentRoutes(svc.commentService)
            bulletCommentRoutes(svc.bulletCommentService)
            channelRoutes(svc.channelService)
        }
        rateLimit(PROXY_ZONE) {
            proxyRoutes(svc.proxyService)
            nicoVideoProxyRoutes(svc.nicoVideoProxyService)
        }
        tokenRoutes(tokenService)
        rateLimit(USER_DATA_ZONE) {
            historyRoutes(svc.historyService, tokenService)
            subscriptionsRoutes(svc.subscriptionsService, tokenService)
            subscriptionFeedRoutes(svc.subscriptionFeedService, tokenService)
            playlistRoutes(svc.playlistService, tokenService)
            watchLaterRoutes(svc.watchLaterService, tokenService)
            progressRoutes(svc.progressService, tokenService)
            favoritesRoutes(svc.favoritesService, tokenService)
            settingsRoutes(svc.settingsService, tokenService)
            searchHistoryRoutes(svc.searchHistoryService, tokenService)
            blockedRoutes(svc.blockedService, tokenService)
        }
    }
}
