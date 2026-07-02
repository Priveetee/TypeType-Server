package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.SabrWebSocketLimits
import dev.typetype.server.services.StreamService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe
import kotlin.math.max

internal class SabrSessionDescriptorHandler(
    private val sabrSessionStore: SabrSessionStore,
    private val streamService: StreamService,
    private val authService: AuthService?,
    private val accessControlService: AccessControlService?,
    private val adminSettingsService: AdminSettingsService?,
) {
    suspend fun handle(call: ApplicationCall, videoId: String) {
        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return
        val url = "https://www.youtube.com/watch?v=$videoId"
        if (access.profile.enabled) {
            when (val result = streamService.getStreamInfo(url)) {
                is ExtractionResult.Success -> {
                    if (!access.profile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) {
                        return call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                    }
                }
                is ExtractionResult.Failure -> return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
                is ExtractionResult.BadRequest -> return call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            }
        }
        val info = fetchSabrInfo(videoId)
            ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))
        val audio = SabrFormatSelector.audio(
            info,
            call.request.queryParameters["audioItag"]?.toIntOrNull(),
            call.request.queryParameters["audioTrackId"],
            requireAac = false,
        ) ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR audio for this video"))
        val video = SabrFormatSelector.video(info, call.request.queryParameters["videoItag"]?.toIntOrNull())
            ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR video for this video"))
        val holder = sabrSessionStore.getOrCreate(videoId, access.userId ?: videoId, info, audio, video)
        sabrSessionStore.ensureWarmed(holder)
        val state = holder.session.streamState
        val endAudio = state.getEndSegment(holder.audioFormat)
        val endVideo = state.getEndSegment(holder.videoFormat)
        if (endAudio <= 0L || endVideo <= 0L) {
            return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("Segment index not yet available"))
        }
        call.respond(buildJsonObject {
            put("videoId", videoId)
            put("session", holder.sessionToken)
            put("transport", "stateful-websocket")
            put("protocol", SabrWebSocketProtocol.PROTOCOL)
            put("maxBinaryFrameBytes", SabrWebSocketLimits.MAX_BINARY_FRAME_BYTES)
            put("durationMs", max(holder.audioFormat.approxDurationMs, holder.videoFormat.approxDurationMs))
            putJsonObject("audio") { putFormat(holder.audioFormat, endAudio) }
            putJsonObject("video") { putFormat(holder.videoFormat, endVideo) }
            putJsonObject("endpoints") {
                put("webSocket", "/sabr/session/$videoId/ws?session=${holder.sessionToken}")
                put("audioInit", initPath(videoId, holder.audioFormat, holder.sessionToken))
                put("videoInit", initPath(videoId, holder.videoFormat, holder.sessionToken))
                put("legacySegmentTemplate", "/sabr/$videoId/{itag}/segment/{sequence}?session=${holder.sessionToken}")
            }
        })
    }

    private suspend fun fetchSabrInfo(videoId: String) = withContext(Dispatchers.IO) {
        withTimeoutOrNull(20_000L) {
            runCatching {
                YoutubeSabrProbe.fetchSabrInfo(videoId, YoutubeSabrClientProfile.WEB, Localization("en", "GB"), ContentCountry("GB"))
            }.getOrNull()
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putFormat(format: YoutubeSabrFormat, endSegment: Long): Unit {
        put("itag", format.itag)
        put("mimeType", format.mimeType)
        put("bitrate", format.bitrate)
        put("endSegment", endSegment)
        put("approxDurationMs", format.approxDurationMs)
        put("qualityLabel", format.qualityLabel)
        put("audioTrackId", format.audioTrackId)
        put("audioTrackName", format.audioTrackDisplayName)
        put("init", initPath("{videoId}", format, "{session}"))
        put("segment", "/sabr/{videoId}/${format.itag}/segment/{sequence}?session={session}")
    }

    private fun initPath(videoId: String, format: YoutubeSabrFormat, session: String): String =
        "/sabr/$videoId/${format.itag}/init?session=$session"
}
