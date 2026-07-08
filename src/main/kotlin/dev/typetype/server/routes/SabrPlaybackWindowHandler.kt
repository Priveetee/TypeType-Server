package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrPlaybackDiagnostics
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class SabrPlaybackWindowHandler(private val sabrSessionStore: SabrSessionStore) {
    suspend fun post(call: ApplicationCall, sessionId: String) {
        val holder = sabrSessionStore.lookupByToken(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session"))
        val request = runCatching { call.receive<SabrPlaybackWindowRequest>() }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid window request"))
        if (!holder.matches(request)) return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Window formats do not match session"))
        if (request.generation != holder.activeGeneration()) return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid generation"))

        holder.setPlayerTimeMs(request.playerTimeMs)
        sabrSessionStore.startPump(holder)
        sabrSessionStore.warmPlaybackAsync(holder)

        val window = buildWindow(holder, request)
        if (window.audio.segments.isNotEmpty() && window.video.segments.isNotEmpty()) {
            return call.respond(HttpStatusCode.OK, window.response)
        }
        call.respond(HttpStatusCode.Accepted, holder.preparingResponse(request, window.blockedBy ?: "window pending"))
    }

    private suspend fun buildWindow(
        holder: SabrSessionHolder,
        request: SabrPlaybackWindowRequest,
    ): WindowBuildResult {
        val audio = buildTrack(holder, holder.audioFormat, request)
        val video = buildTrack(holder, holder.videoFormat, request)
        return WindowBuildResult(
            SabrPlaybackWindowReadyResponse(
                sessionId = holder.sessionToken,
                generation = holder.activeGeneration(),
                ready = true,
                retryAfterMs = null,
                durationMs = holder.durationMs(),
                audio = audio.track,
                video = video.track,
            ),
            audio.blockedBy ?: video.blockedBy,
        )
    }

    private suspend fun buildTrack(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        request: SabrPlaybackWindowRequest,
    ): TrackBuildResult {
        val startSeq = holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(format, request.playerTimeMs.coerceAtLeast(0L))
            .coerceAtLeast(1)
        val goalEndMs = request.playerTimeMs.coerceAtLeast(0L) + request.bufferGoalMs.coerceAtLeast(1L)
        val segments = mutableListOf<SabrPlaybackWindowSegment>()
        var blockedBy: String? = null
        var seq = startSeq
        while (segments.size < MAX_SEGMENTS_PER_TRACK) {
            val segment = sabrSessionStore.cachedSegment(holder, SabrSegmentRequest.media(format, seq))
            if (segment == null) {
                blockedBy = "${format.trackName()}:${format.itag}:$seq pending"
                break
            }
            segments += segment.toWindowSegment(holder, format)
            if (segment.startMs + segment.durationMs >= goalEndMs) break
            seq++
        }
        return TrackBuildResult(
            track = SabrPlaybackWindowTrack(
                mime = format.mimeType.orEmpty(),
                initUrl = "${SabrPlaybackPaths.mediaBasePath(holder.sessionToken)}/${format.itag}/init?generation=${holder.activeGeneration()}",
                segments = segments,
            ),
            blockedBy = blockedBy,
        )
    }

    private fun CachedSabrSegment.toWindowSegment(holder: SabrSessionHolder, format: YoutubeSabrFormat): SabrPlaybackWindowSegment {
        val startMs = startMs.coerceAtLeast(holder.session.streamState.getSegmentStartMs(format, sequence).coerceAtLeast(0L))
        val durationMs = durationMs.takeIf { it > 0L }
            ?: (holder.session.streamState.getSegmentEndMs(format, sequence) - startMs).coerceAtLeast(1L)
        return SabrPlaybackWindowSegment(
            url = "${SabrPlaybackPaths.mediaBasePath(holder.sessionToken)}/${format.itag}/segment/$sequence?generation=${holder.activeGeneration()}",
            startMs = startMs,
            durationMs = durationMs,
        )
    }

    private fun SabrSessionHolder.preparingResponse(request: SabrPlaybackWindowRequest, blockedBy: String): SabrPlaybackWindowPreparingResponse =
        SabrPlaybackWindowPreparingResponse(
            sessionId = sessionToken,
            generation = activeGeneration(),
            ready = false,
            retryAfterMs = RETRY_AFTER_MS,
            status = playbackState().name.lowercase(),
            blockedBy = SabrPlaybackDiagnostics.blocker(this) ?: blockedBy,
            playerTimeMs = request.playerTimeMs.coerceAtLeast(0L),
            readerHeadMs = readerHeadMs(),
            readerTailMs = readerTailMs(),
            bufferedEdgeMs = session.streamState.getMinBufferedEndMs(),
            pendingRefetch = pendingRefetchRequest()?.summary(),
            pendingForwardSeek = pendingForwardSeekRequest()?.summary(),
            terminalError = terminalFailure(),
        )

    private fun SabrSessionHolder.matches(request: SabrPlaybackWindowRequest): Boolean =
        request.videoItag == videoFormat.itag && request.audioItag == audioFormat.itag && request.audioTrackId == audioFormat.audioTrackId

    private fun SabrSessionHolder.durationMs(): Long = maxOf(audioFormat.approxDurationMs, videoFormat.approxDurationMs, 0L)

    private fun YoutubeSabrFormat.trackName(): String = if (isAudio) "audio" else "video"

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"

    private data class WindowBuildResult(
        val response: SabrPlaybackWindowReadyResponse,
        val blockedBy: String?,
    ) {
        val audio: SabrPlaybackWindowTrack = response.audio
        val video: SabrPlaybackWindowTrack = response.video
    }

    private data class TrackBuildResult(
        val track: SabrPlaybackWindowTrack,
        val blockedBy: String?,
    )

    private companion object {
        const val RETRY_AFTER_MS = 500L
        const val MAX_SEGMENTS_PER_TRACK = 12
    }
}
