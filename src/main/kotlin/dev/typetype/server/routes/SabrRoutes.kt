package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.sabrRoutes(
    sabrSessionStore: SabrSessionStore,
    streamService: StreamService,
    authService: AuthService?,
    accessControlService: AccessControlService?,
    adminSettingsService: AdminSettingsService?,
    audioOnlyTokenService: AudioOnlyMediaTokenService?,
) {
    val sessionHandler = SabrSessionDescriptorHandler(
        sabrSessionStore,
        streamService,
        authService,
        accessControlService,
        adminSettingsService,
    )
    val stateHandler = SabrSessionStateHandler(sabrSessionStore)
    val segmentHandler = SabrSegmentHandler(
        sabrSessionStore,
        authService,
        accessControlService,
        adminSettingsService,
    )
    val manifestHandler = SabrManifestHandler(
        sabrSessionStore,
        streamService,
        authService,
        accessControlService,
        adminSettingsService,
        audioOnlyTokenService,
    )
    get("/sabr/session/{videoId}/state") {
        val videoId = call.parameters["videoId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        stateHandler.get(call, videoId)
    }
    post("/sabr/session/{videoId}/state") {
        val videoId = call.parameters["videoId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        stateHandler.post(call, videoId)
    }
    get("/sabr/session/{videoId}") {
        val videoId = call.parameters["videoId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        sessionHandler.handle(call, videoId)
    }
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
