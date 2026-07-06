package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.SabrManifestBuilder
import dev.typetype.server.services.SabrSessionHolder
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText

internal suspend fun ApplicationCall.respondSabrManifest(
    holder: SabrSessionHolder,
    videoId: String,
    audioOnly: Boolean,
    hls: Boolean,
): Unit {
    val state = holder.session.streamState
    val endAudio = state.getEndSegment(holder.audioFormat)
    val endVideo = state.getEndSegment(holder.videoFormat)
    if (endAudio <= 0L || (!audioOnly && !hls && endVideo <= 0L)) {
        return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("Segment index not yet available"))
    }
    val manifest = when {
        hls && audioOnly -> SabrManifestBuilder.buildAudioOnlyHls(
            videoId,
            holder.audioFormat,
            endAudio,
            state,
            holder.sessionToken,
        )
        hls -> SabrManifestBuilder.buildHlsMaster(videoId, holder.audioFormat, holder.videoFormat, holder.sessionToken)
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
    response.headers.append("Cache-Control", "no-store")
    respondText(manifest, if (hls) HLS_CONTENT_TYPE else DASH_CONTENT_TYPE)
}

internal val DASH_CONTENT_TYPE: ContentType = ContentType.parse("application/dash+xml")
internal val HLS_CONTENT_TYPE: ContentType = ContentType.parse("application/vnd.apple.mpegurl")
