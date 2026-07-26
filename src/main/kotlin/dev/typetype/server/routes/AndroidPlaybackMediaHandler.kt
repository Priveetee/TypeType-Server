package dev.typetype.server.routes

import dev.typetype.server.services.AndroidPlaybackMediaResult
import dev.typetype.server.services.AndroidPlaybackPreparationStage
import dev.typetype.server.services.AndroidPlaybackService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal class AndroidPlaybackMediaHandler(private val service: AndroidPlaybackService) {
    suspend fun initialization(call: ApplicationCall, sessionId: String, itag: Int) {
        val session = call.androidPlaybackSession(service, sessionId) ?: return
        val generation = call.validGeneration(sessionId) ?: return
        call.respondMediaResult(service.initialization(session.holder, itag, generation), session)
    }

    suspend fun segment(call: ApplicationCall, sessionId: String, itag: Int, sequence: Int) {
        val session = call.androidPlaybackSession(service, sessionId) ?: return
        val generation = call.validGeneration(sessionId) ?: return
        call.respondMediaResult(service.segment(session.holder, itag, sequence, generation), session)
    }

    private suspend fun ApplicationCall.validGeneration(sessionId: String): Long? {
        if (request.queryParameters["session"] != sessionId) {
            respondAndroidError(
                HttpStatusCode.BadRequest,
                "android_playback_invalid_session",
                "Manifest session does not match the request path",
            )
            return null
        }
        val generation = request.queryParameters["generation"]?.toLongOrNull()
        if (generation == null || generation < 0L) {
            respondAndroidError(
                HttpStatusCode.BadRequest,
                "android_playback_invalid_generation",
                "Missing or invalid Android playback generation",
            )
            return null
        }
        return generation
    }

    private suspend fun ApplicationCall.respondMediaResult(
        result: AndroidPlaybackMediaResult,
        session: dev.typetype.server.services.AndroidPlaybackSession,
    ): Unit = when (result) {
        is AndroidPlaybackMediaResult.Ready -> respondSabrMediaBytes(result.mimeType, result.bytes)
        AndroidPlaybackMediaResult.Preparing -> respond(
            HttpStatusCode.Accepted,
            session.toAndroidPlaybackResponse(
                dev.typetype.server.services.AndroidDashManifestResult.Preparing(
                    AndroidPlaybackPreparationStage.MEDIA_BYTES,
                ),
            ),
        )
        AndroidPlaybackMediaResult.StaleGeneration -> respondAndroidError(
            HttpStatusCode.Conflict,
            "android_playback_stale_generation",
            "Stale Android playback generation",
        )
        AndroidPlaybackMediaResult.TrackNotFound -> respondAndroidError(
            HttpStatusCode.NotFound,
            "android_playback_track_not_found",
            "Android playback track not found",
        )
        AndroidPlaybackMediaResult.InvalidSequence -> respondAndroidError(
            HttpStatusCode.BadRequest,
            "android_playback_invalid_sequence",
            "Invalid Android playback segment sequence",
        )
    }
}
