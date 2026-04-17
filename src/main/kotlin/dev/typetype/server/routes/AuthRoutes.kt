package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.UserProfileItem
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthCookieHelpers
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.authRoutes(authService: AuthService, passwordResetService: PasswordResetService, profileService: ProfileService, adminSettingsService: AdminSettingsService) {
    post("/auth/register") {
        val req = call.receive<RegisterRequest>()
        if (req.email.isBlank() || req.password.isBlank() || req.name.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing fields"))
            return@post
        }
        val registrationAllowed = adminSettingsService.get().allowRegistration || !authService.hasUsers()
        if (!registrationAllowed) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Registration is disabled"))
            return@post
        }
        try {
            val token = authService.register(req.email, req.password, req.name)
            AuthCookieHelpers.setRefreshCookie(call.response, token.refreshToken)
            call.respond(SessionResponse(token.accessToken))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Registration failed"))
        }
    }

    post("/auth/login") {
        val req = call.receive<LoginRequest>()
        val identifier = req.identifier?.trim().orEmpty().ifBlank { req.email?.trim().orEmpty() }
        val token = authService.login(identifier, req.password)
        if (token == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
            return@post
        }
        AuthCookieHelpers.setRefreshCookie(call.response, token.refreshToken)
        call.respond(SessionResponse(token.accessToken))
    }

    post("/auth/refresh") {
        val req = runCatching { call.receive<RefreshRequest>() }.getOrNull()
        val refreshToken = AuthCookieHelpers.extractRefreshToken(call) ?: req?.token
        val newToken = refreshToken?.let { authService.refreshSession(it) }
        if (newToken == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
            return@post
        }
        AuthCookieHelpers.setRefreshCookie(call.response, newToken.refreshToken)
        call.respond(SessionResponse(newToken.accessToken))
    }

    post("/auth/logout") {
        val refreshToken = AuthCookieHelpers.extractRefreshToken(call)
        authService.logout(refreshToken)
        AuthCookieHelpers.clearRefreshCookie(call.response)
        call.respond(HttpStatusCode.NoContent)
    }

    get("/auth/me") {
        val authHeader = call.request.headers["Authorization"]
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing token"))
            return@get
        }
        val token = authHeader.substringAfter("Bearer ")
        val userId = authService.verify(token)
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
            return@get
        }
        val role = authService.getUserRole(userId)
        val profile = if (userId.startsWith("guest:")) null else profileService.getProfile(userId)
        call.respond(
            UserProfileItem(
                id = userId,
                role = role,
                publicUsername = profile?.publicUsername,
                bio = profile?.bio,
                avatarUrl = profile?.avatarUrl,
                avatarType = profile?.avatarType,
                avatarCode = profile?.avatarCode,
            )
        )
    }

    post("/auth/guest") {
        call.respond(AuthResponse(authService.guestLogin()))
    }

    post("/auth/reset-password") {
        val req = runCatching { call.receive<ResetPasswordRequest>() }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            return@post
        }
        if (req.newPassword.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password cannot be blank"))
            return@post
        }
        val ok = passwordResetService.resetPassword(req.resetToken, req.newPassword)
        if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid or expired reset token"))
    }
}
