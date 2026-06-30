package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrManifestBuilder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

fun Route.sabrRoutes(
    sabrSessionStore: SabrSessionStore,
    streamService: StreamService,
    authService: AuthService?,
    accessControlService: AccessControlService?,
) {
    get("/sabr/manifest/{videoId}") {
        val videoId = call.parameters["videoId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        val audioOnly = call.request.queryParameters["audioOnly"].equals("true", ignoreCase = true)
        val hls = audioOnly && call.request.queryParameters["format"].equals("hls", ignoreCase = true)
        val access = call.accessProfileOrRespond(authService, accessControlService) ?: return@get
        val url = "https://www.youtube.com/watch?v=$videoId"

        when (val result = streamService.getStreamInfo(url)) {
            is ExtractionResult.Success -> {
                if (!access.profile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                }
            }
            is ExtractionResult.Failure ->
                return@get call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
            is ExtractionResult.BadRequest ->
                return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
        }

        val userId = access.userId ?: videoId
        val info = withContext(Dispatchers.IO) {
            withTimeoutOrNull(20_000L) {
                runCatching {
                    YoutubeSabrProbe.fetchSabrInfo(
                        videoId,
                        YoutubeSabrClientProfile.WEB,
                        Localization("en", "GB"),
                        ContentCountry("GB"),
                    )
                }.getOrNull()
            }
        } ?: return@get call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))

        val audio = pickSabrAudio(info)
            ?: return@get call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No AAC/mp4a SABR audio for this video"))
        val video = pickSabrVideo(info)
            ?: return@get call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No AVC/mp4 SABR video for this video"))

        val holder = sabrSessionStore.getOrCreate(videoId, userId, info, audio, video)
        sabrSessionStore.ensureWarmed(holder)
        val state = holder.session.streamState
        val endAudio = state.getEndSegment(holder.audioFormat)
        val endVideo = state.getEndSegment(holder.videoFormat)
        if (endAudio <= 0L || endVideo <= 0L) {
            return@get call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("Segment index not yet available"))
        }

        val manifest = if (audioOnly && hls) {
            SabrManifestBuilder.buildAudioOnlyHls(
                videoId,
                holder.audioFormat,
                endAudio,
                state,
                holder.sessionToken,
            )
        } else if (audioOnly) {
            SabrManifestBuilder.buildAudioOnly(
                videoId,
                holder.audioFormat,
                endAudio,
                state,
                holder.sessionToken,
            )
        } else {
            SabrManifestBuilder.build(
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
        call.respondText(
            manifest,
            if (hls) ContentType.parse("application/vnd.apple.mpegurl") else ContentType.parse("application/dash+xml"),
        )
    }

    get("/sabr/{videoId}/{itag}/init") {
        call.serveSabrSegment(sabrSessionStore, authService, accessControlService, isInit = true, seq = 0)
    }
    get("/sabr/{videoId}/{itag}/segment/{seq}") {
        val seq = call.parameters["seq"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid seq"))
        call.serveSabrSegment(sabrSessionStore, authService, accessControlService, isInit = false, seq = seq)
    }
}

private suspend fun ApplicationCall.serveSabrSegment(
    sabrSessionStore: SabrSessionStore,
    authService: AuthService?,
    accessControlService: AccessControlService?,
    isInit: Boolean,
    seq: Int,
) {
    val videoId = parameters["videoId"]
    val itag = parameters["itag"]?.toIntOrNull()
    if (videoId == null || itag == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid path"))
        return
    }
    val sessionToken = request.queryParameters["session"]
    val holder = if (sessionToken != null) {
        sabrSessionStore.lookupByToken(videoId, sessionToken, itag)
    } else {
        val access = accessProfileOrRespond(authService, accessControlService) ?: return
        val userId = access.userId ?: videoId
        sabrSessionStore.lookupByItag(videoId, userId, itag)
    } ?: return respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR session for this request"))
    val format = if (holder.audioFormat.itag == itag) holder.audioFormat else holder.videoFormat
    val request = if (isInit) {
        SabrSegmentRequest.initialization(format)
    } else {
        if (seq < 1) return respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid seq"))
        SabrSegmentRequest.media(format, seq)
    }
    val seg = withTimeout(20_000L) { sabrSessionStore.fetchSegment(holder, request) }
        ?: return respond(HttpStatusCode.NotFound, ErrorResponse("Segment not available"))
    response.headers.append("Cache-Control", "no-store")
    val mime = containerMime(format.mimeType.orEmpty())
    respondOutputStream(mime) {
        write(seg.data)
    }
}

private fun pickSabrVideo(info: YoutubeSabrInfo): YoutubeSabrFormat? =
    info.formats.filter { f ->
        f.isVideo && f.mimeType?.contains("mp4") == true && f.mimeType?.contains("avc1") == true
    }.maxByOrNull { it.height }

private fun pickSabrAudio(info: YoutubeSabrInfo): YoutubeSabrFormat? =
    info.formats.filter { f ->
        f.isAudio && f.mimeType?.contains("mp4") == true && f.mimeType?.contains("mp4a") == true
    }.maxByOrNull { it.bitrate }

private fun containerMime(mime: String): ContentType {
    val container = mime.substringBefore(";").trim().lowercase()
    return when (container) {
        "video/webm" -> ContentType("video", "webm")
        "video/mp4" -> ContentType.Video.MP4
        "audio/webm" -> ContentType("audio", "webm")
        "audio/mp4" -> ContentType("audio", "mp4")
        "audio/ogg", "audio/opus" -> ContentType("audio", "ogg")
        else -> ContentType.Application.OctetStream
    }
}
