package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.SabrManifestBuilder
import dev.typetype.server.services.SabrSessionHolder
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal suspend fun ApplicationCall.respondSabrPlaybackManifest(holder: SabrSessionHolder): Unit {
    val state = holder.session.streamState
    val knownAudio = maxOf(state.getEndSegment(holder.audioFormat), state.getMaxSegment(holder.audioFormat).toLong())
    val knownVideo = maxOf(state.getEndSegment(holder.videoFormat), state.getMaxSegment(holder.videoFormat).toLong())
    if (knownAudio <= 0L || knownVideo <= 0L) {
        return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("Segment index not yet available"))
    }
    val startMs = holder.playerTimeMs().coerceAtLeast(0L)
    val edgeMs = state.getMinBufferedEndMs().coerceAtLeast(startMs)
    val windowEndMs = edgeMs + PLAYBACK_MANIFEST_WINDOW_MS
    val startAudio = segmentAt(holder, holder.audioFormat, startMs, knownAudio)
    val startVideo = segmentAt(holder, holder.videoFormat, startMs, knownVideo)
    val windowEndAudio = segmentAt(holder, holder.audioFormat, windowEndMs, knownAudio)
    val windowEndVideo = segmentAt(holder, holder.videoFormat, windowEndMs, knownVideo)
    val manifest = SabrManifestBuilder.build(
        holder.key.videoId,
        holder.audioFormat,
        holder.videoFormat,
        endSegmentAudio = maxOf(startAudio, windowEndAudio).toLong(),
        endSegmentVideo = maxOf(startVideo, windowEndVideo).toLong(),
        streamState = state,
        sessionToken = holder.sessionToken,
        startSegmentAudio = startAudio,
        startSegmentVideo = startVideo,
        mediaBasePath = SabrPlaybackPaths.mediaBasePath(holder.sessionToken),
    )
    response.headers.append("Cache-Control", "no-store")
    respondText(manifest, DASH_CONTENT_TYPE)
}

private fun segmentAt(holder: SabrSessionHolder, format: YoutubeSabrFormat, ms: Long, maxSegment: Long): Int =
    holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(format, ms)
        .coerceAtLeast(1)
        .coerceAtMost(maxSegment.toInt().coerceAtLeast(1))

private const val PLAYBACK_MANIFEST_WINDOW_MS = 30_000L
