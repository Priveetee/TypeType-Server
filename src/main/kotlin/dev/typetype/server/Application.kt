package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.downloader.OkHttpDownloader
import dev.typetype.server.routes.avatarRoutes
import dev.typetype.server.routes.bulletCommentRoutes
import dev.typetype.server.routes.channelRoutes
import dev.typetype.server.routes.commentRoutes
import dev.typetype.server.routes.downloaderGatewayRoutes
import dev.typetype.server.routes.internalObservabilityRoutes
import dev.typetype.server.routes.manifestRoutes
import dev.typetype.server.routes.nicoVideoProxyRoutes
import dev.typetype.server.routes.podcastRoutes
import dev.typetype.server.routes.proxyRoutes
import dev.typetype.server.routes.storyboardProxyRoutes
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.routes.suggestionRoutes
import dev.typetype.server.routes.adminSessionRoutes
import dev.typetype.server.routes.adminRoutes
import dev.typetype.server.routes.adminBugReportRoutes
import dev.typetype.server.routes.authRoutes
import dev.typetype.server.routes.trendingRoutes
import dev.typetype.server.routes.publicMetadataRoutes
import dev.typetype.server.routes.sessionActivityRoutes
import dev.typetype.server.routes.userDataRoutes
import dev.typetype.server.services.ActiveSessionService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.DownloaderGatewayService
import dev.typetype.server.services.GitHubIssueService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import dev.typetype.server.services.PipePipeBackupImporterService
import dev.typetype.server.services.OpenMojiProxyService
import dev.typetype.server.services.InstanceService
import dev.typetype.server.services.InternalHealthService
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

    val jwtSecret = System.getenv("JWT_SECRET") ?: UUID.randomUUID().toString()
    val authService = AuthService(jwtSecret)
    val userAdminService = UserAdminService()
    val passwordResetService = PasswordResetService()
    val profileService = ProfileService()
    val avatarService = AvatarService()
    val gitHubIssueService = GitHubIssueService()
    val adminSettingsService = AdminSettingsService()
    val activeSessionService = ActiveSessionService(adminSettingsService)
    val instanceService = InstanceService(authService, adminSettingsService)
    val restoreService = PipePipeBackupImporterService()

    val cacheUrl = System.getenv("DRAGONFLY_URL") ?: "redis://localhost:6379"
    val subtitleServiceUrl = System.getenv("SUBTITLE_SERVICE_URL") ?: "http://typetype-token:8081"
    val downloaderServiceUrl = System.getenv("DOWNLOADER_SERVICE_URL") ?: "http://typetype-downloader:18093"
    val cache = DragonflyService(cacheUrl)
    val svc = ServiceRegistry(cache, subtitleServiceUrl)
    val downloaderGatewayService = DownloaderGatewayService(downloaderServiceUrl)
    val openMojiProxyService = OpenMojiProxyService(cache)
    val internalHealthService = InternalHealthService(cache, downloaderGatewayService, subtitleServiceUrl)

    configurePlugins(authService)

    routing {
        internalObservabilityRoutes(internalHealthService::check)
        publicMetadataRoutes(instanceService::getInstance)
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
            podcastRoutes(svc.podcastService)
        }
        rateLimit(PROXY_ZONE) {
            proxyRoutes(svc.proxyService)
            nicoVideoProxyRoutes(svc.nicoVideoProxyService)
        }
        rateLimit(PROXY_STORYBOARD_ZONE) {
            storyboardProxyRoutes(svc.proxyService)
        }
        downloaderGatewayRoutes(downloaderGatewayService)
        authRoutes(authService, passwordResetService, profileService, adminSettingsService, svc.homeRecommendationWarmupService)
        adminRoutes(authService, userAdminService, passwordResetService, adminSettingsService)
        adminSessionRoutes(authService, activeSessionService)
        sessionActivityRoutes(authService, activeSessionService)
        adminBugReportRoutes(authService, svc.bugReportService, gitHubIssueService)
        avatarRoutes(avatarService, openMojiProxyService)
        rateLimit(USER_DATA_ZONE) { userDataRoutes(svc, authService, profileService, avatarService, svc.bugReportService, restoreService) }
    }
}
