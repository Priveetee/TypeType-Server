package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrManifestBuilder
import dev.typetype.server.services.SabrPreparedInfo
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionPurpose
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.bothFormatsKnown
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
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
        val sessionToken = call.request.queryParameters["session"]
        if (hls && playlist != null && call.request.queryParameters["session"] != null) {
            return handleHlsPlaylist(call, videoId, playlist)
        }
        if (sessionToken != null) {
            val holder = sabrSessionStore.lookupByToken(videoId, sessionToken)
                ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR session for this request"))
            sabrSessionStore.startPump(holder)
            return call.respondSabrManifest(holder, videoId, audioOnly, hls)
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
        val prepared = sabrSessionStore.fetchInfo(videoId, startTimeMs, cachedFirst = true)
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
        val purpose = if (call.request.queryParameters["workload"] == "download") {
            SabrSessionPurpose.DOWNLOAD
        } else {
            SabrSessionPurpose.MANIFEST
        }
        val holder = createHolder(videoId, userId, prepared, audio, video, startTimeMs, purpose)
        if (audioOnly) {
            holder.setActiveTracks(videoActive = false, audioActive = true)
            sabrSessionStore.ensureWarmed(holder)
        } else if (!bothFormatsKnown(holder)) {
            val readyHolder = preflightOrRecreate(videoId, userId, prepared, audio, video, startTimeMs, purpose, holder)
            if (readyHolder == null) {
                return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR preflight failed"))
            }
            sabrSessionStore.startPump(readyHolder)
            return call.respondSabrManifest(readyHolder, videoId, audioOnly, hls)
        }
        sabrSessionStore.startPump(holder)
        call.respondSabrManifest(holder, videoId, audioOnly, hls)
    }

    private suspend fun preflightOrRecreate(
        videoId: String,
        userId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        startTimeMs: Long,
        purpose: SabrSessionPurpose,
        holder: SabrSessionHolder,
    ): SabrSessionHolder? {
        if (preflight(holder, startTimeMs)) return holder
        sabrSessionStore.release(holder)
        val refreshed = sabrSessionStore.fetchInfo(videoId, startTimeMs, cachedFirst = false) ?: prepared
        val refreshedAudio = SabrFormatSelector.audio(refreshed.info, audio.itag, audio.audioTrackId, requireAac = true)
            ?: return null
        val refreshedVideo = SabrFormatSelector.video(refreshed.info, video.itag) ?: return null
        val fresh = createHolder(videoId, userId, refreshed, refreshedAudio, refreshedVideo, startTimeMs, purpose)
        return fresh.takeIf { preflight(it, startTimeMs) }
    }

    private suspend fun preflight(holder: SabrSessionHolder, startTimeMs: Long): Boolean =
        withTimeoutOrNull(PREFLIGHT_TIMEOUT_MS) { sabrSessionStore.preflightPlayback(holder, startTimeMs) } == true

    private fun createHolder(
        videoId: String,
        userId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        startTimeMs: Long,
        purpose: SabrSessionPurpose,
    ): SabrSessionHolder = sabrSessionStore.getOrCreate(
        videoId,
        userId,
        prepared.info,
        audio,
        video,
        prepared.initialToken,
        startTimeMs,
        startPump = false,
        purpose = purpose,
    )

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
        const val PREFLIGHT_TIMEOUT_MS = 25_000L
    }
}
