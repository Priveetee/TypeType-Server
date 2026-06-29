package dev.typetype.server

import dev.typetype.server.routes.audioOnlyContractRoutes
import dev.typetype.server.routes.audioOnlySourceRoutes
import dev.typetype.server.routes.manifestRoutes
import dev.typetype.server.routes.nicoVideoProxyRoutes
import dev.typetype.server.routes.proxyRoutes
import dev.typetype.server.routes.storyboardProxyRoutes
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route

internal fun Route.installStreamRoutes(
    svc: ServiceRegistry,
    authService: AuthService,
    adminSettingsService: AdminSettingsService,
) {
    rateLimit(STREAMS_ZONE) {
        streamRoutes(
            streamService = svc.streamService,
            authService = authService,
            accessControlService = svc.accessControlService,
            youtubeSessionStreamInfo = svc.youtubeSessionStreamService?.let { service ->
                { userId, url -> service.getStreamInfo(userId, url) }
            },
            adminSettingsService = adminSettingsService,
            publicHlsManifestTokenService = svc.publicHlsManifestTokenService,
        )
        audioOnlyContractRoutes(
            streamService = svc.streamService,
            tokenService = svc.audioOnlyMediaTokenService,
            authService = authService,
            youtubeSessionStreamInfo = svc.youtubeSessionStreamService?.let { service ->
                { userId, url -> service.getStreamInfo(userId, url) }
            },
            accessControlService = svc.accessControlService,
            adminSettingsService = adminSettingsService,
            publicHlsManifestTokenService = svc.publicHlsManifestTokenService,
        )
        manifestRoutes(
            svc.manifestService,
            svc.nativeManifestService,
            svc.hlsManifestService,
            svc.youtubeSessionHlsManifestService,
            authService,
            adminSettingsService,
            svc.publicHlsManifestTokenService,
        )
    }
}

internal fun Route.installProxyRoutes(svc: ServiceRegistry) {
    rateLimit(PROXY_ZONE) {
        proxyRoutes(svc.proxyService)
        audioOnlySourceRoutes(
            streamService = svc.streamService,
            proxyService = svc.proxyService,
            tokenService = svc.audioOnlyMediaTokenService,
            youtubeSessionStreamInfo = svc.youtubeSessionStreamService?.let { service ->
                { userId, url -> service.getStreamInfo(userId, url) }
            },
        )
        nicoVideoProxyRoutes(svc.nicoVideoProxyService)
    }
    rateLimit(PROXY_STORYBOARD_ZONE) { storyboardProxyRoutes(svc.proxyService) }
}
