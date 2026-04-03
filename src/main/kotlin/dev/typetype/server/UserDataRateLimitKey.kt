package dev.typetype.server

import dev.typetype.server.services.AuthService
import io.ktor.server.application.ApplicationCall

fun userDataRateLimitKey(call: ApplicationCall, authService: AuthService): String {
    val bearerToken = call.request.headers["Authorization"]
        ?.takeIf { it.startsWith("Bearer ") }
        ?.substringAfter("Bearer ")
    val userId = bearerToken?.let(authService::verify)
    if (userId != null) return "user:$userId"
    return "ip:${call.request.headers["X-Real-IP"] ?: call.request.local.remoteHost}"
}
