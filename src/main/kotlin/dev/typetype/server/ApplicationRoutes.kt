package dev.typetype.server

import dev.typetype.server.routes.adminBugReportRoutes
import dev.typetype.server.routes.adminRoutes
import dev.typetype.server.routes.adminSessionRoutes
import dev.typetype.server.routes.authRoutes
import dev.typetype.server.routes.avatarRoutes
import dev.typetype.server.routes.bulletCommentRoutes
import dev.typetype.server.routes.channelRoutes
import dev.typetype.server.routes.commentRoutes
import dev.typetype.server.routes.downloaderGatewayRoutes
import dev.typetype.server.routes.internalObservabilityRoutes
import dev.typetype.server.routes.manifestRoutes
import dev.typetype.server.routes.nicoVideoProxyRoutes
import dev.typetype.server.routes.oidcAuthRoutes
import dev.typetype.server.routes.podcastRoutes
import dev.typetype.server.routes.proxyRoutes
import dev.typetype.server.routes.publicMetadataRoutes
import dev.typetype.server.routes.publicPlaylistRoutes
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.routes.sessionActivityRoutes
import dev.typetype.server.routes.storyboardProxyRoutes
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.routes.suggestionRoutes
import dev.typetype.server.routes.trendingRoutes
import dev.typetype.server.routes.userDataRoutes
import dev.typetype.server.routes.youtubeRemoteBrowserRoutes
import dev.typetype.server.services.ActiveSessionService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.DownloaderGatewayService
import dev.typetype.server.services.GitHubIssueService
import dev.typetype.server.services.InstanceService
import dev.typetype.server.services.InternalHealthService
import dev.typetype.server.services.OidcAuthService
import dev.typetype.server.services.OpenMojiProxyService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.PipePipeBackupImporterService
import dev.typetype.server.services.ProfileService
import dev.typetype.server.services.UserAdminService
import dev.typetype.server.services.YoutubeRemoteBrowserService
import io.ktor.server.application.Application
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing

internal fun Application.installApplicationRoutes(
    svc: ServiceRegistry,
    authService: AuthService,
    adminSettingsService: AdminSettingsService,
    activeSessionService: ActiveSessionService,
    downloaderGatewayService: DownloaderGatewayService,
    gitHubIssueService: GitHubIssueService,
    instanceService: InstanceService,
    oidcAuthService: OidcAuthService,
    passwordResetService: PasswordResetService,
    profileService: ProfileService,
    userAdminService: UserAdminService,
    avatarService: AvatarService,
    openMojiProxyService: OpenMojiProxyService,
    internalHealthService: InternalHealthService,
    restoreService: PipePipeBackupImporterService,
    youtubeRemoteBrowserService: YoutubeRemoteBrowserService,
) {
    routing {
        internalObservabilityRoutes(internalHealthService::check)
        publicMetadataRoutes(instanceService::getInstance)
        rateLimit(STREAMS_ZONE) {
            streamRoutes(svc.streamService, authService, svc.youtubeSessionStreamService::getStreamInfo)
            manifestRoutes(svc.manifestService, svc.nativeManifestService, svc.hlsManifestService)
        }
        rateLimit(EXTRACTION_ZONE) {
            searchRoutes(svc.searchService)
            suggestionRoutes(svc.suggestionService)
            trendingRoutes(svc.trendingService)
            publicPlaylistRoutes(svc.publicPlaylistService)
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
        rateLimit(PROXY_STORYBOARD_ZONE) { storyboardProxyRoutes(svc.proxyService) }
        downloaderGatewayRoutes(downloaderGatewayService)
        oidcAuthRoutes(oidcAuthService, adminSettingsService)
        authRoutes(authService, passwordResetService, profileService, adminSettingsService, svc.homeRecommendationWarmupService)
        adminRoutes(authService, userAdminService, passwordResetService, adminSettingsService)
        adminSessionRoutes(authService, activeSessionService)
        sessionActivityRoutes(authService, activeSessionService)
        adminBugReportRoutes(authService, svc.bugReportService, gitHubIssueService)
        avatarRoutes(avatarService, openMojiProxyService)
        rateLimit(USER_DATA_ZONE) { youtubeRemoteBrowserRoutes(youtubeRemoteBrowserService, authService) }
        rateLimit(USER_DATA_ZONE) { userDataRoutes(svc, authService, profileService, avatarService, svc.bugReportService, restoreService) }
    }
}
