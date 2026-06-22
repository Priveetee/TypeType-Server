package dev.typetype.server.routes

import dev.typetype.server.services.AccessControlProfile
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AuthService
import dev.typetype.server.models.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal data class AccessRouteProfile(
    val userId: String?,
    val profile: AccessControlProfile,
)

internal suspend fun ApplicationCall.accessProfileOrRespond(
    authService: AuthService?,
    accessControlService: AccessControlService?,
): AccessRouteProfile? {
    if (authService == null) {
        return AccessRouteProfile(userId = null, profile = AccessControlProfile.unrestricted)
    }
    val authHeader = request.headers["Authorization"]
    if (authHeader == null) {
        val profile = accessControlService?.profileFor(null) ?: AccessControlProfile.unrestricted
        return AccessRouteProfile(userId = null, profile = profile)
    }
    if (!authHeader.startsWith("Bearer ")) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
        return null
    }
    val userId = authService.verify(authHeader.substringAfter("Bearer "))
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
        return null
    }
    val profile = accessControlService?.profileFor(userId) ?: AccessControlProfile.unrestricted
    return AccessRouteProfile(userId = userId, profile = profile)
}
