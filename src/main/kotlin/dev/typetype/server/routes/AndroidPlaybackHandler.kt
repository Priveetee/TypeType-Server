package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AndroidDashManifestResult
import dev.typetype.server.services.AndroidPlaybackCreateResult
import dev.typetype.server.services.AndroidPlaybackSeekResult
import dev.typetype.server.services.AndroidPlaybackService
import dev.typetype.server.services.AndroidSubtitleInventoryCoordinator
import dev.typetype.server.services.AndroidSubtitleInventorySnapshot
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.runCatchingNonCancellation
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.response.respondText

internal class AndroidPlaybackHandler(
    private val store: SabrSessionStore,
    private val streamService: StreamService,
    private val authService: AuthService?,
    private val accessControlService: AccessControlService?,
    private val adminSettingsService: AdminSettingsService?,
    private val subtitleCoordinator: AndroidSubtitleInventoryCoordinator,
    val service: AndroidPlaybackService = AndroidPlaybackService(store),
) {
    suspend fun create(call: ApplicationCall, videoId: String) {
        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return
        if (!validateAccess(call, videoId, access)) return
        val requestResult = runCatchingNonCancellation { call.receiveNullable<AndroidPlaybackCreateRequest>() }
        if (requestResult.isFailure) {
            return call.respondAndroidError(
                HttpStatusCode.BadRequest,
                "android_playback_invalid_request",
                "Invalid playback request",
            )
        }
        val request = requestResult.getOrNull() ?: AndroidPlaybackCreateRequest()
        val subtitleInventory = subtitleCoordinator.start(videoId)
        val prepared = store.fetchInfo(videoId, cachedFirst = true)
        prepared
            ?: return call.respondAndroidError(
                HttpStatusCode.UnprocessableEntity,
                "android_playback_probe_failed",
                "SABR probe failed",
            )
        val subtitles = when (val inventory = subtitleInventory.await()) {
            is AndroidSubtitleInventorySnapshot.Ready -> inventory.tracks
            AndroidSubtitleInventorySnapshot.Preparing,
            AndroidSubtitleInventorySnapshot.TemporaryFailure,
            -> return call.respondAndroidError(
                HttpStatusCode.ServiceUnavailable,
                "android_subtitle_inventory_unavailable",
                "Android subtitle inventory is temporarily unavailable",
            )
        }
        val audio = SabrFormatSelector.audio(prepared.info, request.audioItag, request.audioTrackId, requireAac = true)
            ?: return call.respondAndroidError(
                HttpStatusCode.UnprocessableEntity,
                "android_playback_audio_unavailable",
                "No compatible SABR audio for this video",
            )
        val video = SabrFormatSelector.androidVideo(prepared.info, request.videoItag)
            ?: return call.respondAndroidError(
                HttpStatusCode.UnprocessableEntity,
                "android_playback_video_unavailable",
                "No compatible SABR video for this video",
            )
        when (
            val result = service.create(
                videoId,
                access.userId ?: "guest",
                prepared,
                audio,
                video,
                subtitles,
            )
        ) {
            is AndroidPlaybackCreateResult.Created -> call.respondSession(
                result.session.toAndroidPlaybackResponse(result.manifest),
                result.manifest,
            )
            AndroidPlaybackCreateResult.UnsupportedLive -> call.respondAndroidError(
                HttpStatusCode.UnprocessableEntity,
                "android_live_playback_unsupported",
                "Android live playback is not supported",
            )
        }
    }

    suspend fun seek(call: ApplicationCall, sessionId: String) {
        val session = call.androidPlaybackSession(service, sessionId) ?: return
        val holder = session.holder
        val requestResult = runCatchingNonCancellation { call.receiveNullable<AndroidPlaybackSeekRequest>() }
        val request = requestResult.getOrNull()
        if (requestResult.isFailure || request == null) {
            return call.respondAndroidError(
                HttpStatusCode.BadRequest,
                "android_playback_invalid_request",
                "Invalid seek request",
            )
        }
        if (request.playerTimeMs < 0L) {
            return call.respondAndroidError(
                HttpStatusCode.BadRequest,
                "android_playback_invalid_seek",
                "Invalid seek position",
            )
        }
        when (val result = service.seek(session, request.generation, request.playerTimeMs)) {
            is AndroidPlaybackSeekResult.Ready -> call.respondSession(
                session.withHolder(result.holder).toAndroidPlaybackResponse(result.manifest),
                result.manifest,
            )
            AndroidPlaybackSeekResult.StaleGeneration -> call.respondAndroidError(
                HttpStatusCode.Conflict,
                "android_playback_stale_generation",
                "Stale Android playback generation",
            )
        }
    }

    suspend fun manifest(call: ApplicationCall, sessionId: String) {
        call.response.headers.append("Cache-Control", "no-store")
        val session = call.androidPlaybackSession(service, sessionId) ?: return
        when (val result = service.manifest(session)) {
            is AndroidDashManifestResult.Ready -> {
                call.respondText(result.manifest, DASH_CONTENT_TYPE)
            }
            is AndroidDashManifestResult.Preparing -> call.respond(
                HttpStatusCode.Accepted,
                session.toAndroidPlaybackResponse(result),
            )
            AndroidDashManifestResult.UnsupportedLive -> call.respondAndroidError(
                HttpStatusCode.UnprocessableEntity,
                "android_live_playback_unsupported",
                "Android live playback is not supported",
            )
            is AndroidDashManifestResult.Invalid -> call.respondAndroidError(
                HttpStatusCode.UnprocessableEntity,
                "android_playback_invalid_index",
                result.reason,
            )
            is AndroidDashManifestResult.TemporaryFailure -> call.respondAndroidError(
                HttpStatusCode.ServiceUnavailable,
                result.code,
                result.reason,
            )
        }
    }

    private suspend fun ApplicationCall.respondSession(
        response: AndroidPlaybackResponse,
        manifest: AndroidDashManifestResult,
    ): Unit = when (manifest) {
        is AndroidDashManifestResult.Ready -> respond(HttpStatusCode.OK, response)
        is AndroidDashManifestResult.Preparing -> respond(HttpStatusCode.Accepted, response)
        AndroidDashManifestResult.UnsupportedLive -> respondAndroidError(
            HttpStatusCode.UnprocessableEntity,
            "android_live_playback_unsupported",
            "Android live playback is not supported",
        )
        is AndroidDashManifestResult.Invalid -> respondAndroidError(
            HttpStatusCode.UnprocessableEntity,
            "android_playback_invalid_index",
            manifest.reason,
        )
        is AndroidDashManifestResult.TemporaryFailure -> respondAndroidError(
            HttpStatusCode.ServiceUnavailable,
            manifest.code,
            manifest.reason,
        )
    }

    private suspend fun validateAccess(call: ApplicationCall, videoId: String, access: AccessRouteProfile): Boolean {
        if (!access.profile.enabled) return true
        return when (val result = streamService.getStreamInfo("https://www.youtube.com/watch?v=$videoId")) {
            is ExtractionResult.Success -> if (access.profile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) {
                true
            } else {
                call.respondAndroidError(HttpStatusCode.Forbidden, "channel_not_allowed", "Channel is not allowed")
                false
            }
            is ExtractionResult.Failure -> {
                call.respondAndroidError(HttpStatusCode.UnprocessableEntity, "android_playback_extraction_failed", result.message)
                false
            }
            is ExtractionResult.BadRequest -> {
                call.respondAndroidError(HttpStatusCode.BadRequest, "android_playback_invalid_video", result.message)
                false
            }
        }
    }
}
