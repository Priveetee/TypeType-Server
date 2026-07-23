package dev.typetype.server.routes

import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AndroidPlaybackService
import dev.typetype.server.services.AndroidPlaybackSession
import dev.typetype.server.services.AndroidSubtitleContentResult
import dev.typetype.server.services.AndroidSubtitleInventorySnapshot
import dev.typetype.server.services.AndroidSubtitleService
import dev.typetype.server.services.AuthService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes

internal class AndroidSubtitleHandler(
    private val playbackService: AndroidPlaybackService,
    private val subtitleService: AndroidSubtitleService,
    private val authService: AuthService?,
    private val accessControlService: AccessControlService?,
    private val adminSettingsService: AdminSettingsService?,
) {
    suspend fun content(call: ApplicationCall, sessionId: String, trackId: String) {
        call.response.headers.append("Cache-Control", "no-store")
        val session = call.authorizedSession(sessionId) ?: return
        val track = session.subtitles.firstOrNull { it.id == trackId }
            ?: return call.notFound()
        when (val result = subtitleService.content(session.holder.key.videoId, track)) {
            is AndroidSubtitleContentResult.Ready -> call.respondBytes(
                result.bytes,
                ContentType.parse("text/vtt; charset=utf-8"),
                HttpStatusCode.OK,
            )
            AndroidSubtitleContentResult.TemporaryFailure -> call.respondAndroidError(
                HttpStatusCode.ServiceUnavailable,
                "android_subtitle_upstream_unavailable",
                "Android subtitle is temporarily unavailable",
            )
            AndroidSubtitleContentResult.Unavailable -> call.respondAndroidError(
                HttpStatusCode.UnprocessableEntity,
                "android_subtitle_unavailable",
                "Android subtitle cannot be produced",
            )
        }
    }

    suspend fun inventory(call: ApplicationCall, sessionId: String) {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        val session = call.authorizedSession(sessionId) ?: return
        when (val inventory = session.subtitleInventory.snapshot()) {
            AndroidSubtitleInventorySnapshot.Preparing -> {
                call.response.headers.append(HttpHeaders.RetryAfter, "1")
                call.respond(HttpStatusCode.Accepted, AndroidSubtitleInventoryPreparingResponse())
            }
            is AndroidSubtitleInventorySnapshot.Ready -> call.respond(
                HttpStatusCode.OK,
                AndroidSubtitleInventoryReadyResponse(
                    tracks = inventory.tracks.map { it.toResponse(sessionId) },
                ),
            )
            AndroidSubtitleInventorySnapshot.TemporaryFailure -> {
                call.response.headers.append(HttpHeaders.RetryAfter, "1")
                call.respondAndroidError(
                    HttpStatusCode.ServiceUnavailable,
                    "android_subtitle_inventory_unavailable",
                    "Android subtitle inventory is temporarily unavailable",
                )
            }
        }
    }

    private suspend fun ApplicationCall.authorizedSession(sessionId: String): AndroidPlaybackSession? {
        val access = accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return null
        val session = androidPlaybackSession(playbackService, sessionId) ?: return null
        if (session.holder.key.userId == (access.userId ?: "guest")) return session
        notFound()
        return null
    }

    private suspend fun ApplicationCall.notFound() {
        respondAndroidError(
            HttpStatusCode.NotFound,
            "android_subtitle_not_found",
            "Android subtitle track not found",
        )
    }
}
