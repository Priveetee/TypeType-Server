package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SettingsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.settingsRoutes(settingsService: SettingsService, authService: AuthService) {
    get("/settings") {
        call.withJwtAuth(authService) { userId -> call.respond(settingsService.get(userId)) }
    }
    put("/settings") {
        call.withJwtAuth(authService) { userId ->
            val settings = runCatching { call.receive<SettingsItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(settingsService.upsert(userId, settings))
        }
    }
}
