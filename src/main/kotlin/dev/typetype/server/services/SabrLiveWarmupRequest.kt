package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrBufferedRange
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal data class SabrLiveWarmupTarget(val sequence: Int, val timeMs: Long)

internal fun SabrSessionHolder.liveWarmupTarget(): SabrLiveWarmupTarget? {
    val live = livePlaybackSnapshot()
        ?.takeIf { it.active && it.headSequence > 1L && it.headTimeMs > 0L }
        ?: return null
    val segmentDurationMs = (live.headTimeMs / live.headSequence)
        .coerceIn(MIN_LIVE_SEGMENT_DURATION_MS, MAX_LIVE_SEGMENT_DURATION_MS)
    val segmentsBehind = Math.floorDiv(
        live.targetLatencyMs + segmentDurationMs - 1L,
        segmentDurationMs,
    )
    val targetSequence = (live.headSequence - segmentsBehind)
        .coerceIn(1L, Int.MAX_VALUE.toLong())
        .toInt()
    val targetTimeMs = (live.headTimeMs - live.targetLatencyMs).coerceAtLeast(0L)
    return SabrLiveWarmupTarget(targetSequence, targetTimeMs)
}

internal inline fun <T> withLiveWarmupRequestShape(
    holder: SabrSessionHolder,
    target: SabrLiveWarmupTarget?,
    block: () -> T,
): T {
    target ?: return block()
    val state = holder.session.streamState
    val ranges = buildList {
        if (holder.isAudioActive()) add(holder.audioFormat.liveWarmupRange(target.sequence, target.timeMs))
        if (holder.isVideoActive()) add(holder.videoFormat.liveWarmupRange(target.sequence, target.timeMs))
    }
    state.setPlayerTimeMs(target.timeMs)
    state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
    state.setBufferedRangesOverride(ranges)
    return try {
        block()
    } finally {
        state.setBufferedRangesOverride(null)
        state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
    }
}

private fun YoutubeSabrFormat.liveWarmupRange(targetSequence: Int, targetTimeMs: Long): SabrBufferedRange {
    val bufferedSequence = (targetSequence - 1).coerceAtLeast(0)
    return SabrBufferedRange(
        itag,
        lastModified,
        xtags,
        0L,
        targetTimeMs.coerceAtLeast(1L),
        if (bufferedSequence > 0) 1 else 0,
        bufferedSequence,
        TIMESCALE,
    )
}

private const val MIN_LIVE_SEGMENT_DURATION_MS = 100L
private const val MAX_LIVE_SEGMENT_DURATION_MS = 30_000L
private const val TIMESCALE = 1_000
