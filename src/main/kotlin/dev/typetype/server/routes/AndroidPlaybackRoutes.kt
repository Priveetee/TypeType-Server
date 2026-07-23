package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AndroidSubtitleService
import dev.typetype.server.services.AndroidSubtitleInventoryCoordinator
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
    subtitleService: AndroidSubtitleService,
    subtitleCoordinator: AndroidSubtitleInventoryCoordinator,
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
        subtitleCoordinator = subtitleCoordinator,
    )
    val media = AndroidPlaybackMediaHandler(handler.service)
    val subtitles = AndroidSubtitleHandler(
        handler.service,
        subtitleService,
        authService,
        accessControlService,
        adminSettingsService,
    )
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
    get("/android/youtube/playback/{sessionId}/subtitles/{trackId}.vtt") {
        val sessionId = call.parameters["sessionId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        val trackId = call.parameters["trackId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing trackId"))
        subtitles.content(call, sessionId, trackId)
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
