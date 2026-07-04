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
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText

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
        val hls = audioOnly && call.request.queryParameters["format"].equals("hls", ignoreCase = true)
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
        )
        sabrSessionStore.ensureWarmed(holder)
        val state = holder.session.streamState
        val endAudio = state.getEndSegment(holder.audioFormat)
        val endVideo = state.getEndSegment(holder.videoFormat)
        if (endAudio <= 0L || endVideo <= 0L) {
            return call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("Segment index not yet available"),
            )
        }
        val manifest = when {
            audioOnly && hls -> SabrManifestBuilder.buildAudioOnlyHls(
                videoId,
                holder.audioFormat,
                endAudio,
                state,
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

    private companion object {
        val DASH_CONTENT_TYPE: ContentType = ContentType.parse("application/dash+xml")
        val HLS_CONTENT_TYPE: ContentType = ContentType.parse("application/vnd.apple.mpegurl")
    }
}
