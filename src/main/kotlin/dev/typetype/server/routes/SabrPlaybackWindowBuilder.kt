package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class SabrPlaybackWindowBuilder(private val sabrSessionStore: SabrSessionStore) {
    suspend fun build(
        holder: SabrSessionHolder,
        request: SabrPlaybackWindowRequest,
    ): SabrPlaybackWindowBuildResult {
        val audio = buildTrack(holder, holder.audioFormat, request)
        val video = buildTrack(holder, holder.videoFormat, request)
        return SabrPlaybackWindowBuildResult(
            response = SabrPlaybackWindowReadyResponse(
                sessionId = holder.sessionToken,
                generation = holder.activeGeneration(),
                ready = true,
                retryAfterMs = null,
                durationMs = holder.durationMs(),
                audio = audio.track,
                video = video.track,
            ),
            blockedBy = audio.blockedBy ?: video.blockedBy,
            blockedRequest = audio.blockedRequest ?: video.blockedRequest,
        )
    }

    private suspend fun buildTrack(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        request: SabrPlaybackWindowRequest,
    ): TrackBuildResult {
        val startSeq = holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(
            format,
            request.playerTimeMs.coerceAtLeast(0L),
        ).coerceAtLeast(1)
        val goalEndMs = request.playerTimeMs.coerceAtLeast(0L) + request.bufferGoalMs.coerceAtLeast(1L)
        val segments = mutableListOf<SabrPlaybackWindowSegment>()
        var blockedBy: String? = null
        var blockedRequest: SabrSegmentRequest? = null
        var seq = startSeq
        var coveredEndMs = request.playerTimeMs.coerceAtLeast(0L)
        while (segments.size < MAX_SEGMENTS_PER_TRACK) {
            val mediaRequest = SabrSegmentRequest.media(format, seq)
            val segment = sabrSessionStore.cachedSegment(holder, mediaRequest)
            if (segment == null) {
                blockedBy = "${format.trackName()}:${format.itag}:$seq pending"
                blockedRequest = mediaRequest
                break
            }
            segments += segment.toWindowSegment(holder, format)
            coveredEndMs = segment.startMs + segment.durationMs
            if (coveredEndMs >= goalEndMs) break
            seq++
        }
        if (blockedBy == null && coveredEndMs < goalEndMs) {
            blockedBy = "${format.trackName()}:${format.itag}:$seq window capped"
        }
        return TrackBuildResult(
            track = SabrPlaybackWindowTrack(
                mime = format.mimeType.orEmpty(),
                initUrl = "${SabrPlaybackPaths.mediaBasePath(holder.sessionToken)}/${format.itag}/init?generation=${holder.activeGeneration()}",
                segments = segments,
            ),
            blockedBy = blockedBy,
            blockedRequest = blockedRequest,
        )
    }

    private fun CachedSabrSegment.toWindowSegment(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): SabrPlaybackWindowSegment {
        val startMs = startMs.coerceAtLeast(holder.session.streamState.getSegmentStartMs(format, sequence).coerceAtLeast(0L))
        val durationMs = durationMs.takeIf { it > 0L }
            ?: (holder.session.streamState.getSegmentEndMs(format, sequence) - startMs).coerceAtLeast(1L)
        return SabrPlaybackWindowSegment(
            url = "${SabrPlaybackPaths.mediaBasePath(holder.sessionToken)}/${format.itag}/segment/$sequence?generation=${holder.activeGeneration()}",
            startMs = startMs,
            durationMs = durationMs,
        )
    }

    private fun SabrSessionHolder.durationMs(): Long = maxOf(audioFormat.approxDurationMs, videoFormat.approxDurationMs, 0L)

    private fun YoutubeSabrFormat.trackName(): String = if (isAudio) "audio" else "video"

    private data class TrackBuildResult(
        val track: SabrPlaybackWindowTrack,
        val blockedBy: String?,
        val blockedRequest: SabrSegmentRequest?,
    )

    private companion object {
        const val MAX_SEGMENTS_PER_TRACK = 12
    }
}

internal data class SabrPlaybackWindowBuildResult(
    val response: SabrPlaybackWindowReadyResponse,
    val blockedBy: String?,
    val blockedRequest: SabrSegmentRequest?,
) {
    val isReady: Boolean = blockedBy == null && response.audio.segments.isNotEmpty() && response.video.segments.isNotEmpty()
}
