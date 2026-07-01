package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.sabrRoutes(
    sabrSessionStore: SabrSessionStore,
    streamService: StreamService,
    authService: AuthService?,
    accessControlService: AccessControlService?,
    adminSettingsService: AdminSettingsService?,
) {
    val manifestHandler = SabrManifestHandler(
        sabrSessionStore,
        streamService,
        authService,
        accessControlService,
        adminSettingsService,
    )
    val segmentHandler = SabrSegmentHandler(sabrSessionStore, authService, accessControlService, adminSettingsService)
    get("/sabr/manifest/{videoId}") {
        val videoId = call.parameters["videoId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        manifestHandler.handle(call, videoId)
    }
    get("/sabr/{videoId}/{itag}/init") {
        segmentHandler.handle(call, isInit = true, seq = 0)
    }
    get("/sabr/{videoId}/{itag}/segment/{seq}") {
        val seq = call.parameters["seq"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid seq"))
        segmentHandler.handle(call, isInit = false, seq = seq)
    }
}
