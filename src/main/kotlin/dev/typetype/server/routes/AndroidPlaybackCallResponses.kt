package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AndroidPlaybackSessionLookup
import dev.typetype.server.services.AndroidPlaybackSession
import dev.typetype.server.services.AndroidPlaybackService
import dev.typetype.server.services.SabrSessionHolder
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal suspend fun ApplicationCall.androidPlaybackHolder(
    service: AndroidPlaybackService,
    sessionId: String,
): SabrSessionHolder? = androidPlaybackSession(service, sessionId)?.holder

internal suspend fun ApplicationCall.androidPlaybackSession(
    service: AndroidPlaybackService,
    sessionId: String,
): AndroidPlaybackSession? = when (val result = service.lookup(sessionId)) {
    is AndroidPlaybackSessionLookup.Active -> result.session
    AndroidPlaybackSessionLookup.Expired -> {
        respondAndroidError(HttpStatusCode.Gone, "android_playback_expired", "Android playback session expired")
        null
    }
    AndroidPlaybackSessionLookup.Unknown -> {
        respondAndroidError(HttpStatusCode.NotFound, "android_playback_not_found", "Android playback session not found")
        null
    }
}

internal suspend fun ApplicationCall.respondAndroidError(
    status: HttpStatusCode,
    code: String,
    message: String,
): Unit = respond(status, ErrorResponse(message, code))
