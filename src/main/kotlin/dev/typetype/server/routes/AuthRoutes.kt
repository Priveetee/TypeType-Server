package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val email: String, val password: String, val name: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val token: String)

@Serializable
data class AuthResponse(val token: String)

@Serializable
private data class ResetPasswordRequest(val resetToken: String, val newPassword: String)

fun Route.authRoutes(authService: AuthService, passwordResetService: PasswordResetService) {
    post("/auth/register") {
        val req = call.receive<RegisterRequest>()
        if (req.email.isBlank() || req.password.isBlank() || req.name.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing fields"))
            return@post
        }
        try {
            val token = authService.register(req.email, req.password, req.name)
            call.respond(AuthResponse(token))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Registration failed"))
        }
    }

    post("/auth/login") {
        val req = call.receive<LoginRequest>()
        val token = authService.login(req.email, req.password)
        if (token == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
            return@post
        }
        call.respond(AuthResponse(token))
    }

    post("/auth/refresh") {
        val req = call.receive<RefreshRequest>()
        val newToken = authService.refreshToken(req.token)
        if (newToken == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
            return@post
        }
        call.respond(AuthResponse(newToken))
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
        call.respond(mapOf("id" to userId, "role" to role))
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
