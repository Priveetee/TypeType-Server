package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrInitializationData
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.findCachedMediaAt
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
        val video = buildTrack(holder, holder.videoFormat, request, request.playerTimeMs)
        val decodeStartMs = video.track.segments.firstOrNull()?.startMs ?: request.playerTimeMs
        val audio = buildTrack(holder, holder.audioFormat, request, minOf(request.playerTimeMs, decodeStartMs))
        val blocked = blockedTrack(audio, video)
        val readyEndMs = minOf(holder.durationMs(), request.playerTimeMs.coerceAtLeast(0L) + MIN_READY_AHEAD_MS)
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
            isReady = audio.covers(readyEndMs) && video.covers(readyEndMs),
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
        requestedStartMs: Long,
    ): TrackBuildResult {
        val targetMs = requestedStartMs.coerceAtLeast(0L)
        val goalEndMs = request.playerTimeMs.coerceAtLeast(0L) + request.bufferGoalMs.coerceAtLeast(1L)
        val segments = mutableListOf<SabrPlaybackWindowSegment>()
        var blockedBy: String? = null
        var blockedRequest: SabrSegmentRequest? = null
        var seq = holder.playbackStartSequence(format, targetMs)
        var rebased = false
        var coveredEndMs = targetMs
        while (segments.size < MAX_SEGMENTS_PER_TRACK) {
            val mediaRequest = SabrSegmentRequest.media(format, seq)
            val segment = sabrSessionStore.cachedSegment(holder, mediaRequest)
            if (segments.isEmpty() && !rebased && (segment == null || !segment.covers(targetMs))) {
                val authoritative = holder.session.findCachedMediaAt(format, targetMs, seq)
                if (authoritative != null) {
                    seq = authoritative.header.sequenceNumber
                    holder.session.streamState.jumpBufferedTo(format, seq)
                    rebased = true
                    continue
                }
            }
            if (segment == null) {
                blockedBy = "${format.trackName()}:${format.itag}:$seq pending"
                blockedRequest = mediaRequest
                break
            }
            if (segments.isEmpty() && segment.startMs > targetMs && seq > 1) {
                seq = previousSequence(seq, segment, targetMs)
                continue
            }
            val windowSegment = segment.toWindowSegment(holder, format)
            segments += windowSegment
            coveredEndMs = windowSegment.startMs + windowSegment.durationMs
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
            coveredEndMs = coveredEndMs,
        )
    }

    private fun previousSequence(sequence: Int, segment: CachedSabrSegment, targetMs: Long): Int {
        val durationMs = segment.durationMs.coerceAtLeast(1L)
        val leadMs = segment.startMs - targetMs
        val count = ((leadMs + durationMs - 1L) / durationMs).coerceAtLeast(1L)
        return (sequence - count.coerceAtMost((sequence - 1).toLong()).toInt()).coerceAtLeast(1)
    }

    private fun CachedSabrSegment.covers(targetMs: Long): Boolean =
        startMs >= 0L && durationMs > 0L && targetMs >= startMs && targetMs < startMs + durationMs

    private fun CachedSabrSegment.toWindowSegment(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): SabrPlaybackWindowSegment {
        val startMs = startMs.takeIf { it >= 0L }
            ?: holder.session.streamState.getSegmentStartMs(format, sequence).coerceAtLeast(0L)
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
        val coveredEndMs: Long,
    ) {
        fun covers(requiredEndMs: Long): Boolean = track.segments.isNotEmpty() && coveredEndMs >= requiredEndMs
    }

    private companion object {
        const val MAX_SEGMENTS_PER_TRACK = 12
        const val MIN_READY_AHEAD_MS = 1_000L
    }
}

internal data class SabrPlaybackWindowBuildResult(
    val response: SabrPlaybackWindowReadyResponse,
    val blockedBy: String?,
    val blockedRequest: SabrSegmentRequest?,
    val isReady: Boolean,
)
