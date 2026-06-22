package dev.typetype.server.routes

import dev.typetype.server.ServiceRegistry
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.BugReportService
import dev.typetype.server.services.PipePipeBackupImporterService
import dev.typetype.server.services.ProfileService
import io.ktor.server.routing.Route

internal fun Route.userDataRoutes(
    svc: ServiceRegistry,
    authService: AuthService,
    profileService: ProfileService,
    avatarService: AvatarService,
    bugReportService: BugReportService,
    restoreService: PipePipeBackupImporterService,
) {
    historyRoutes(svc.historyService, authService)
    subscriptionsRoutes(svc.subscriptionsService, authService, svc.homeRecommendationWarmupService)
    subscriptionFeedRoutes(svc.subscriptionFeedService, authService)
    subscriptionShortsFeedRoutes(svc.subscriptionShortsFeedService, authService)
    playlistRoutes(svc.playlistService, authService)
    savedPlaylistRoutes(svc.savedPlaylistService, svc.publicPlaylistService, authService)
    watchLaterRoutes(svc.watchLaterService, authService)
    progressRoutes(svc.progressService, authService)
    favoritesRoutes(svc.favoritesService, authService)
    settingsRoutes(svc.settingsService, authService)
    searchHistoryRoutes(svc.searchHistoryService, authService)
    allowedChannelsRoutes(svc.allowedChannelsService, authService)
    blockedRoutes(svc.blockedService, authService)
    notificationsRoutes(svc.notificationsService, authService)
    youtubeSessionRoutes(svc.youtubeSessionService, authService)
    youtubeTakeoutImportRoutes(svc.youtubeTakeoutImportService, authService)
    profileRoutes(profileService, avatarService, authService)
    bugReportRoutes(bugReportService, authService)
    restoreRoutes(restoreService, authService)
    homeRecommendationRoutes(svc.homeRecommendationService, authService, svc.accessControlService)
    homeRecommendationShortsRoutes(svc.homeRecommendationService, authService, svc.accessControlService)
}
