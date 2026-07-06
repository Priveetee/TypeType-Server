package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrManifestBuilder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.bothFormatsKnown
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.coroutines.withTimeoutOrNull

internal class SabrManifestHandler(
    private val sabrSessionStore: SabrSessionStore,
    private val streamService: StreamService,
    private val authService: AuthService?,
    private val accessControlService: AccessControlService?,
    private val adminSettingsService: AdminSettingsService?,
    private val audioOnlyTokenService: AudioOnlyMediaTokenService?,
) {
    private val accessResolver = SabrManifestAccessResolver(audioOnlyTokenService)

    suspend fun handle(call: ApplicationCall, videoId: String) {
        val audioOnly = call.request.queryParameters["audioOnly"].equals("true", ignoreCase = true)
        val hls = call.request.queryParameters["format"].equals("hls", ignoreCase = true)
        val playlist = call.request.queryParameters["playlist"]
        if (hls && playlist != null && call.request.queryParameters["session"] != null) {
            return handleHlsPlaylist(call, videoId, playlist)
        }
        val manifestAccess = accessResolver.resolve(call, videoId) ?: return
        val access = when (manifestAccess) {
            is SabrManifestAccess.AudioOnlyToken -> null
            SabrManifestAccess.RequiresAuth ->
                call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return
        }
        val url = "https://www.youtube.com/watch?v=$videoId"
        if (access?.profile?.enabled == true) {
            when (val result = streamService.getStreamInfo(url)) {
                is ExtractionResult.Success -> {
                    if (!access.profile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) {
                        return call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                    }
                }
                is ExtractionResult.Failure ->
                    return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
                is ExtractionResult.BadRequest ->
                    return call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            }
        }
        val startTimeMs = call.request.queryParameters["playerTimeMs"]?.toLongOrNull()?.coerceAtLeast(0L)
            ?: call.request.queryParameters["startTimeMs"]?.toLongOrNull()?.coerceAtLeast(0L)
            ?: 0L
        val prepared = sabrSessionStore.fetchInfo(videoId, startTimeMs)
            ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))
        val audioToken = (manifestAccess as? SabrManifestAccess.AudioOnlyToken)?.token
        val audio = SabrFormatSelector.audio(
            prepared.info,
            audioToken?.selectedItag ?: call.request.queryParameters["audioItag"]?.toIntOrNull(),
            audioToken?.selectedAudioTrackId ?: call.request.queryParameters["audioTrackId"],
            requireAac = true,
        )
            ?: return call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("No SABR audio for this video"),
            )
        val video = SabrFormatSelector.video(
            prepared.info,
            call.request.queryParameters["videoItag"]?.toIntOrNull(),
        )
            ?: return call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("No SABR video for this video"),
            )
        val userId = audioToken?.userId ?: access?.userId ?: videoId
        val holder = sabrSessionStore.getOrCreate(
            videoId,
            userId,
            prepared.info,
            audio,
            video,
            prepared.initialToken,
            startTimeMs,
            startPump = false,
        )
        if (audioOnly) {
            sabrSessionStore.ensureWarmed(holder)
        } else if (!bothFormatsKnown(holder)) {
            val preflight = withTimeoutOrNull(PREFLIGHT_TIMEOUT_MS) {
                sabrSessionStore.preflightPlayback(holder, startTimeMs)
            } == true
            if (!preflight) {
                return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR preflight failed"))
            }
        }
        sabrSessionStore.startPump(holder)
        val state = holder.session.streamState
        val endAudio = state.getEndSegment(holder.audioFormat)
        val endVideo = state.getEndSegment(holder.videoFormat)
        if (endAudio <= 0L || (!audioOnly && endVideo <= 0L)) {
            return call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("Segment index not yet available"),
            )
        }
        val manifest = when {
            hls && audioOnly -> SabrManifestBuilder.buildAudioOnlyHls(
                videoId,
                holder.audioFormat,
                endAudio,
                state,
                holder.sessionToken,
            )
            hls -> SabrManifestBuilder.buildHlsMaster(
                videoId,
                holder.audioFormat,
                holder.videoFormat,
                holder.sessionToken,
            )
            audioOnly -> SabrManifestBuilder.buildAudioOnly(
                videoId,
                holder.audioFormat,
                endAudio,
                state,
                holder.sessionToken,
            )
            else -> SabrManifestBuilder.build(
                videoId,
                holder.audioFormat,
                holder.videoFormat,
                endAudio,
                endVideo,
                state,
                holder.sessionToken,
            )
        }
        call.response.headers.append("Cache-Control", "no-store")
        call.respondText(manifest, if (hls) HLS_CONTENT_TYPE else DASH_CONTENT_TYPE)
    }

    private suspend fun handleHlsPlaylist(call: ApplicationCall, videoId: String, playlist: String) {
        val holder = sabrSessionStore.lookupByToken(videoId, call.request.queryParameters["session"].orEmpty())
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR session for this request"))
        sabrSessionStore.ensureWarmed(holder)
        val state = holder.session.streamState
        val manifest = when (playlist) {
            "audio" -> SabrManifestBuilder.buildAudioOnlyHls(
                videoId,
                holder.audioFormat,
                state.getEndSegment(holder.audioFormat),
                state,
                holder.sessionToken,
            )
            "video" -> SabrManifestBuilder.buildVideoOnlyHls(
                videoId,
                holder.videoFormat,
                state.getEndSegment(holder.videoFormat),
                state,
                holder.sessionToken,
            )
            else -> return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid HLS playlist"))
        }
        call.response.headers.append("Cache-Control", "no-store")
        call.respondText(manifest, HLS_CONTENT_TYPE)
    }

    private companion object {
        val DASH_CONTENT_TYPE: ContentType = ContentType.parse("application/dash+xml")
        val HLS_CONTENT_TYPE: ContentType = ContentType.parse("application/vnd.apple.mpegurl")
        const val PREFLIGHT_TIMEOUT_MS = 25_000L
    }
}
