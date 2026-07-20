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
import io.ktor.server.routing.post

internal fun Route.androidPlaybackRoutes(
    store: SabrSessionStore,
    streamService: StreamService,
    authService: AuthService?,
    accessControlService: AccessControlService?,
    adminSettingsService: AdminSettingsService?,
) {
    val handler = AndroidPlaybackHandler(
        store,
        streamService,
        authService,
        accessControlService,
        adminSettingsService,
    )
    val media = AndroidPlaybackMediaHandler(handler.service)
    post("/android/youtube/playback/{videoId}") {
        val videoId = call.parameters["videoId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        handler.create(call, videoId)
    }
    post("/android/youtube/playback/{sessionId}/seek") {
        val sessionId = call.parameters["sessionId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        handler.seek(call, sessionId)
    }
    get("/android/youtube/playback/{sessionId}/manifest.mpd") {
        val sessionId = call.parameters["sessionId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        handler.manifest(call, sessionId)
    }
    get("/android/youtube/playback/{sessionId}/{itag}/init") {
        val sessionId = call.parameters["sessionId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        val itag = call.parameters["itag"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid itag"))
        media.initialization(call, sessionId, itag)
    }
    get("/android/youtube/playback/{sessionId}/{itag}/segment/{sequence}") {
        val sessionId = call.parameters["sessionId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        val itag = call.parameters["itag"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid itag"))
        val sequence = call.parameters["sequence"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid sequence"))
        media.segment(call, sessionId, itag, sequence)
    }
}
