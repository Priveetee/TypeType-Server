package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.UserAdminService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

@Serializable
private data class RoleBody(val role: String)

@Serializable
private data class ResetTokenResponse(val resetToken: String)

fun Route.adminRoutes(
    authService: AuthService,
    userAdminService: UserAdminService,
    passwordResetService: PasswordResetService,
    adminSettingsService: AdminSettingsService,
) {
    get("/admin/users") {
        call.withAdminAuth(authService) { _ ->
            call.respond(userAdminService.listUsers())
        }
    }

    post("/admin/users/{id}/suspend") {
        call.withAdminAuth(authService) { _ ->
            val id = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val ok = userAdminService.suspendUser(id)
            if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
        }
    }

    delete("/admin/users/{id}/suspend") {
        call.withAdminAuth(authService) { _ ->
            val id = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val ok = userAdminService.unsuspendUser(id)
            if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
        }
    }

    put("/admin/users/{id}/role") {
        call.withAdminAuth(authService) { _ ->
            val id = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val body = runCatching { call.receive<RoleBody>() }.getOrElse {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val ok = userAdminService.promoteUser(id, body.role)
            if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid role or user not found"))
        }
    }

    post("/admin/users/{id}/reset-token") {
        call.withAdminAuth(authService) { _ ->
            val id = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val token = passwordResetService.generateToken(id)
            call.respond(HttpStatusCode.Created, ResetTokenResponse(resetToken = token))
        }
    }

    get("/admin/settings") {
        call.withAdminAuth(authService) { _ ->
            call.respond(adminSettingsService.get())
        }
    }

    put("/admin/settings") {
        call.withAdminAuth(authService) { _ ->
            val body = runCatching { call.receive<AdminSettingsItem>() }.getOrElse {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(adminSettingsService.upsert(body))
        }
    }
}
