package dev.typetype.server.routes

import dev.typetype.server.services.AndroidPlaybackMediaResult
import dev.typetype.server.services.AndroidPlaybackService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal class AndroidPlaybackMediaHandler(private val service: AndroidPlaybackService) {
    suspend fun initialization(call: ApplicationCall, sessionId: String, itag: Int) {
        val holder = call.androidPlaybackHolder(service, sessionId) ?: return
        val generation = call.validGeneration(sessionId) ?: return
        call.respondMediaResult(service.initialization(holder, itag, generation), holder)
    }

    suspend fun segment(call: ApplicationCall, sessionId: String, itag: Int, sequence: Int) {
        val holder = call.androidPlaybackHolder(service, sessionId) ?: return
        val generation = call.validGeneration(sessionId) ?: return
        call.respondMediaResult(service.segment(holder, itag, sequence, generation), holder)
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
        holder: dev.typetype.server.services.SabrSessionHolder,
    ): Unit = when (result) {
        is AndroidPlaybackMediaResult.Ready -> respondSabrMediaBytes(result.mimeType, result.bytes)
        AndroidPlaybackMediaResult.Preparing -> respond(
            HttpStatusCode.Accepted,
            holder.toAndroidPlaybackResponse(dev.typetype.server.services.AndroidDashManifestResult.Preparing),
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
