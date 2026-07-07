package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrPreparedInfo
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class SabrPlaybackHandler(
    private val sabrSessionStore: SabrSessionStore,
    private val streamService: StreamService,
    private val authService: AuthService?,
    private val accessControlService: AccessControlService?,
    private val adminSettingsService: AdminSettingsService?,
) {
    suspend fun create(call: ApplicationCall, videoId: String) {
        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return
        if (!validateAccess(call, videoId, access)) return
        val request = call.playbackRequest()
        val startTimeMs = request.effectiveStartTimeMs()
        val prepared = sabrSessionStore.fetchInfo(videoId, startTimeMs, cachedFirst = true)
            ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))
        val audio = selectAudio(call, prepared, request) ?: return
        val video = selectVideo(call, prepared, request) ?: return
        val holder = sabrSessionStore.getOrCreate(
            videoId,
            access.userId ?: videoId,
            prepared.info,
            audio,
            video,
            prepared.initialToken,
            startTimeMs,
            startPump = false,
        )
        holder.setPlayerTimeMs(startTimeMs)
        respondPrepared(call, holder, videoId, startTimeMs)
    }

    suspend fun seek(call: ApplicationCall, sessionId: String) {
        val holder = sabrSessionStore.lookupByToken(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session"))
        val request = call.playbackRequest()
        val playerTimeMs = request.effectiveStartTimeMs()
        holder.setPlayerTimeMs(playerTimeMs)
        respondPrepared(call, holder, holder.key.videoId, playerTimeMs)
    }

    suspend fun manifest(call: ApplicationCall, sessionId: String) {
        val holder = sabrSessionStore.lookupByToken(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session"))
        sabrSessionStore.startPump(holder)
        call.respondSabrPlaybackManifest(holder)
    }

    suspend fun segment(call: ApplicationCall, sessionId: String, isInit: Boolean, seq: Int) {
        val holder = sabrSessionStore.lookupByToken(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session"))
        val itag = call.parameters["itag"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid itag"))
        val format = holder.formatForItag(itag)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR track for this request"))
        if (isInit) {
            val bytes = withTimeoutOrNull(PLAYBACK_SEGMENT_TIMEOUT_MS) {
                sabrSessionStore.fetchInitializationData(holder, format)
            } ?: return call.respond(HttpStatusCode.Accepted, holder.toRetryPlaybackResponse("preparing", RETRY_AFTER_MS))
            return call.respondSabrMediaBytes(format.mimeType.orEmpty(), bytes)
        }
        if (seq < 1) return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid seq"))
        val segment = sabrSessionStore.fetchPlaybackSegment(holder, format, seq, PLAYBACK_SEGMENT_TIMEOUT_MS)
            ?: return call.respond(HttpStatusCode.Accepted, holder.toRetryPlaybackResponse("repositioning", RETRY_AFTER_MS))
        call.respondSabrMediaBytes(format.mimeType.orEmpty(), segment.data)
    }

    private suspend fun respondPrepared(
        call: ApplicationCall,
        holder: SabrSessionHolder,
        videoId: String,
        startTimeMs: Long,
    ) {
        val ready = withTimeoutOrNull(PLAYBACK_READY_TIMEOUT_MS) {
            sabrSessionStore.preflightPlayback(holder, startTimeMs)
        } == true
        sabrSessionStore.startPump(holder)
        val response = holder.toPlaybackResponse(videoId, startTimeMs, ready, RETRY_AFTER_MS)
        call.respond(if (ready) HttpStatusCode.OK else HttpStatusCode.Accepted, response)
    }

    private suspend fun validateAccess(call: ApplicationCall, videoId: String, access: AccessRouteProfile): Boolean {
        if (!access.profile.enabled) return true
        return when (val result = streamService.getStreamInfo("https://www.youtube.com/watch?v=$videoId")) {
            is ExtractionResult.Success -> {
                if (access.profile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) true else {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                    false
                }
            }
            is ExtractionResult.Failure -> {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
                false
            }
            is ExtractionResult.BadRequest -> {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
                false
            }
        }
    }

    private suspend fun ApplicationCall.playbackRequest(): SabrPlaybackRequest {
        val body = runCatching { receive<SabrPlaybackRequest>() }.getOrNull()
        return SabrPlaybackRequest(
            videoItag = body?.videoItag ?: request.queryParameters["videoItag"]?.toIntOrNull(),
            audioItag = body?.audioItag ?: request.queryParameters["audioItag"]?.toIntOrNull(),
            audioTrackId = body?.audioTrackId ?: request.queryParameters["audioTrackId"],
            startTimeMs = body?.startTimeMs ?: request.queryParameters["startTimeMs"]?.toLongOrNull(),
            playerTimeMs = body?.playerTimeMs ?: request.queryParameters["playerTimeMs"]?.toLongOrNull(),
        )
    }

    private fun SabrPlaybackRequest.effectiveStartTimeMs(): Long =
        (playerTimeMs ?: startTimeMs ?: 0L).coerceAtLeast(0L)

    private suspend fun selectAudio(
        call: ApplicationCall,
        prepared: SabrPreparedInfo,
        request: SabrPlaybackRequest,
    ): YoutubeSabrFormat? = SabrFormatSelector.audio(
        prepared.info,
        request.audioItag,
        request.audioTrackId,
        requireAac = true,
    ) ?: run {
        call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR audio for this video"))
        null
    }

    private suspend fun selectVideo(
        call: ApplicationCall,
        prepared: SabrPreparedInfo,
        request: SabrPlaybackRequest,
    ): YoutubeSabrFormat? = SabrFormatSelector.video(prepared.info, request.videoItag) ?: run {
        call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR video for this video"))
        null
    }

    private fun SabrSessionHolder.formatForItag(itag: Int): YoutubeSabrFormat? = when (itag) {
        audioFormat.itag -> audioFormat
        videoFormat.itag -> videoFormat
        else -> null
    }

    private companion object {
        const val PLAYBACK_READY_TIMEOUT_MS = 20_000L
        const val PLAYBACK_SEGMENT_TIMEOUT_MS = 20_000L
        const val RETRY_AFTER_MS = 1_000L
    }
}
