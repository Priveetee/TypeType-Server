package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrInitializationData
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.playbackStartSequence
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class SabrPlaybackWindowBuilder(private val sabrSessionStore: SabrSessionStore) {
    suspend fun build(
        holder: SabrSessionHolder,
        request: SabrPlaybackWindowRequest,
    ): SabrPlaybackWindowBuildResult {
        SabrInitializationData.ingestRemembered(holder.audioFormat, holder)
        SabrInitializationData.ingestRemembered(holder.videoFormat, holder)
        val audio = buildTrack(holder, holder.audioFormat, request)
        val video = buildTrack(holder, holder.videoFormat, request)
        val blocked = blockedTrack(audio, video)
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
            blockedBy = blocked?.blockedBy,
            blockedRequest = blocked?.blockedRequest,
        )
    }

    private fun blockedTrack(audio: TrackBuildResult, video: TrackBuildResult): TrackBuildResult? = when {
        audio.track.segments.isEmpty() && audio.blockedRequest != null -> audio
        video.track.segments.isEmpty() && video.blockedRequest != null -> video
        audio.blockedRequest != null -> audio
        video.blockedRequest != null -> video
        else -> null
    }

    private suspend fun buildTrack(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        request: SabrPlaybackWindowRequest,
    ): TrackBuildResult {
        val targetMs = request.playerTimeMs.coerceAtLeast(0L)
        val goalEndMs = targetMs + request.bufferGoalMs.coerceAtLeast(1L)
        val segments = mutableListOf<SabrPlaybackWindowSegment>()
        var blockedBy: String? = null
        var blockedRequest: SabrSegmentRequest? = null
        var seq = holder.playbackStartSequence(format, targetMs)
        var coveredEndMs = targetMs
        while (segments.size < MAX_SEGMENTS_PER_TRACK) {
            val mediaRequest = SabrSegmentRequest.media(format, seq)
            val segment = sabrSessionStore.cachedSegment(holder, mediaRequest)
            if (segment == null) {
                blockedBy = "${format.trackName()}:${format.itag}:$seq pending"
                blockedRequest = mediaRequest
                break
            }
            if (segments.isEmpty() && segment.startMs > targetMs && seq > 1) {
                seq = previousSequence(seq, segment, targetMs)
                continue
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

    private fun previousSequence(sequence: Int, segment: CachedSabrSegment, targetMs: Long): Int {
        val durationMs = segment.durationMs.coerceAtLeast(1L)
        val leadMs = segment.startMs - targetMs
        val count = ((leadMs + durationMs - 1L) / durationMs).coerceAtLeast(1L)
        return (sequence - count.coerceAtMost((sequence - 1).toLong()).toInt()).coerceAtLeast(1)
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
    val isReady: Boolean = response.audio.segments.isNotEmpty() && response.video.segments.isNotEmpty()
}
