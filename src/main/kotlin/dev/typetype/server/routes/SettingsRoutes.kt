package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.TokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.settingsRoutes(settingsService: SettingsService, tokenService: TokenService) {
    get("/settings") {
        call.withAuth(tokenService) { call.respond(settingsService.get()) }
    }
    put("/settings") {
        call.withAuth(tokenService) {
            val settings = runCatching { call.receive<SettingsItem>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(settingsService.upsert(settings))
        }
    }
}
