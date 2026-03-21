package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.downloader.OkHttpDownloader
import dev.typetype.server.routes.blockedRoutes
import dev.typetype.server.routes.avatarRoutes
import dev.typetype.server.routes.bulletCommentRoutes
import dev.typetype.server.routes.channelRoutes
import dev.typetype.server.routes.commentRoutes
import dev.typetype.server.routes.favoritesRoutes
import dev.typetype.server.routes.historyRoutes
import dev.typetype.server.routes.homeRecommendationRoutes
import dev.typetype.server.routes.manifestRoutes
import dev.typetype.server.routes.nicoVideoProxyRoutes
import dev.typetype.server.routes.playlistRoutes
import dev.typetype.server.routes.profileRoutes
import dev.typetype.server.routes.progressRoutes
import dev.typetype.server.routes.proxyRoutes
import dev.typetype.server.routes.restoreRoutes
import dev.typetype.server.routes.storyboardProxyRoutes
import dev.typetype.server.routes.searchHistoryRoutes
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.routes.settingsRoutes
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.routes.subscriptionFeedRoutes
import dev.typetype.server.routes.subscriptionsRoutes
import dev.typetype.server.routes.suggestionRoutes
import dev.typetype.server.routes.adminRoutes
import dev.typetype.server.routes.authRoutes
import dev.typetype.server.routes.trendingRoutes
import dev.typetype.server.routes.watchLaterRoutes
import dev.typetype.server.routes.tokenRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import dev.typetype.server.services.PipePipeBackupImporterService
import dev.typetype.server.services.TokenService
import dev.typetype.server.services.UserAdminService
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing
import org.schabi.newpipe.extractor.NewPipe
import java.util.UUID

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    NewPipe.init(OkHttpDownloader.instance())
    launchExtractorLifecycle()

    val dbUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/typetype"
    val dbUser = System.getenv("DATABASE_USER") ?: "typetype"
    val dbPassword = System.getenv("DATABASE_PASSWORD") ?: "typetype"
    DatabaseFactory.init(dbUrl, dbUser, dbPassword)

    val tokenService = TokenService()
    val jwtSecret = System.getenv("JWT_SECRET") ?: UUID.randomUUID().toString()
    val authService = AuthService(jwtSecret)
    val userAdminService = UserAdminService()
    val passwordResetService = PasswordResetService()
    val profileService = ProfileService()
    val avatarService = AvatarService()
    val adminSettingsService = AdminSettingsService()
    val restoreService = PipePipeBackupImporterService()

    val cacheUrl = System.getenv("DRAGONFLY_URL") ?: "redis://localhost:6379"
    val subtitleServiceUrl = System.getenv("SUBTITLE_SERVICE_URL") ?: "http://typetype-token:8081"
    val svc = ServiceRegistry(DragonflyService(cacheUrl), subtitleServiceUrl)

    configurePlugins()

    routing {
        rateLimit(STREAMS_ZONE) {
            streamRoutes(svc.streamService)
            manifestRoutes(svc.manifestService, svc.nativeManifestService, svc.hlsManifestService)
        }
        rateLimit(EXTRACTION_ZONE) {
            searchRoutes(svc.searchService)
            suggestionRoutes(svc.suggestionService)
            trendingRoutes(svc.trendingService)
            commentRoutes(svc.commentService)
            bulletCommentRoutes(svc.bulletCommentService)
        }
        rateLimit(CHANNEL_ZONE) {
            channelRoutes(svc.channelService)
        }
        rateLimit(PROXY_ZONE) {
            proxyRoutes(svc.proxyService)
            nicoVideoProxyRoutes(svc.nicoVideoProxyService)
        }
        rateLimit(PROXY_STORYBOARD_ZONE) {
            storyboardProxyRoutes(svc.proxyService)
        }
        tokenRoutes(tokenService)
        authRoutes(authService, passwordResetService, profileService)
        adminRoutes(authService, userAdminService, passwordResetService, adminSettingsService)
        avatarRoutes(avatarService)
        rateLimit(USER_DATA_ZONE) {
            historyRoutes(svc.historyService, authService)
            subscriptionsRoutes(svc.subscriptionsService, authService)
            subscriptionFeedRoutes(svc.subscriptionFeedService, authService)
            playlistRoutes(svc.playlistService, authService)
            watchLaterRoutes(svc.watchLaterService, authService)
            progressRoutes(svc.progressService, authService)
            favoritesRoutes(svc.favoritesService, authService)
            settingsRoutes(svc.settingsService, authService)
            searchHistoryRoutes(svc.searchHistoryService, authService)
            blockedRoutes(svc.blockedService, authService)
            profileRoutes(profileService, avatarService, authService)
            restoreRoutes(restoreService, authService)
            homeRecommendationRoutes(svc.homeRecommendationService, authService)
        }
    }
}
