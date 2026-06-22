package dev.typetype.server.routes

import dev.typetype.server.services.AdminUserLookupService
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.UserAdminService
import io.ktor.server.routing.Route

fun Route.adminAllowListRoutes(
    authService: AuthService,
    userAdminService: UserAdminService,
    userLookupService: AdminUserLookupService,
    allowedChannelsService: AllowedChannelsService,
    allowedPlaylistsService: AllowedPlaylistsService,
) {
    adminUserAccessModeRoutes(authService, userAdminService)
    adminUserSearchRoutes(authService, userLookupService)
    adminUserAllowListRoutes(authService, userLookupService, allowedChannelsService, allowedPlaylistsService)
    adminAllowedPlaylistRoutes(authService, allowedPlaylistsService)
    adminUserAllowedRoutes(authService, userLookupService, allowedChannelsService, allowedPlaylistsService)
}
