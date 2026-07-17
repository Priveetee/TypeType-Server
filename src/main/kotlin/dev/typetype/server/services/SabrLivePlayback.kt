package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal data class SabrLivePlaybackSnapshot(
    val active: Boolean,
    val postLiveDvr: Boolean,
    val headSequence: Long,
    val headTimeMs: Long,
    val seekableStartMs: Long,
    val seekableEndMs: Long,
    val atLiveEdge: Boolean,
    val targetLatencyMs: Long,
)

internal fun SabrSessionHolder.livePlaybackSnapshot(): SabrLivePlaybackSnapshot? {
    val state = session.streamState
    val postLiveDvr = runCatching { state.isPostLiveDvr }.getOrDefault(false)
    val sessionLive = runCatching { session.isLive }.getOrDefault(false)
    val stateLive = runCatching { state.isLive }.getOrDefault(false)
    val detectedLive = expectsLive() || sessionLive || stateLive || postLiveDvr
    if (!detectedLive) return null

    val observedEndMs = maxOf(
        state.observedEndMs(audioFormat),
        state.observedEndMs(videoFormat),
        state.getBufferedEndMs(audioFormat),
        state.getBufferedEndMs(videoFormat),
        state.getMinBufferedEndMs(),
        0L,
    )
    val reportedHeadTimeMs = runCatching { state.liveHeadTimeMs }.getOrDefault(0L)
    val headTimeMs = reportedHeadTimeMs.takeIf { it > 0L } ?: observedEndMs
    val active = !postLiveDvr && (expectsLive() || sessionLive || stateLive)
    val seekableEndMs = if (active) headTimeMs else observedEndMs
    val seekableStartMs = if (active) {
        (seekableEndMs - LIVE_DVR_WINDOW_MS).coerceAtLeast(0L)
    } else {
        0L
    }
    val extractorAtLiveEdge = runCatching { session.isAtLiveEdge }.getOrDefault(false)
    val readerAtLiveEdge = headTimeMs - maxOf(playerTimeMs(), readerHeadMs()) <= LIVE_EDGE_TOLERANCE_MS
    return SabrLivePlaybackSnapshot(
        active = active,
        postLiveDvr = postLiveDvr,
        headSequence = maxOf(
            runCatching { session.liveHeadSequenceNumber }.getOrDefault(0L),
            runCatching { state.liveHeadSequenceNumber }.getOrDefault(0L),
            0L,
        ),
        headTimeMs = headTimeMs,
        seekableStartMs = seekableStartMs,
        seekableEndMs = seekableEndMs,
        atLiveEdge = active && (extractorAtLiveEdge || readerAtLiveEdge),
        targetLatencyMs = LIVE_TARGET_LATENCY_MS,
    )
}

internal fun SabrSessionHolder.resolvePlaybackStartMs(requestedStartMs: Long): Long {
    val requested = requestedStartMs.coerceAtLeast(0L)
    val live = livePlaybackSnapshot() ?: return requested
    if (!live.active) return requested.coerceAtMost(live.seekableEndMs.takeIf { it > 0L } ?: requested)
    if (requested > 0L) return requested.coerceIn(live.seekableStartMs, live.seekableEndMs)
    return (live.seekableEndMs - live.targetLatencyMs).coerceAtLeast(live.seekableStartMs)
}

internal fun SabrSessionHolder.isFutureLiveRequest(request: SabrSegmentRequest): Boolean {
    if (request.isInitializationSegment) return false
    val live = livePlaybackSnapshot()?.takeIf { it.active && it.headSequence > 0L } ?: return false
    return request.sequenceNumber > live.headSequence &&
        request.sequenceNumber <= live.headSequence + LIVE_FUTURE_SEGMENT_TOLERANCE
}

internal fun SabrSessionHolder.liveRetryAfterMs(blockedRequests: List<SabrSegmentRequest> = emptyList()): Long =
    if (livePlaybackSnapshot()?.active == true &&
        (blockedRequests.isEmpty() || blockedRequests.all(::isFutureLiveRequest))
    ) LIVE_EDGE_POLL_MS else DEFAULT_PLAYBACK_RETRY_MS

private fun org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState.observedEndMs(
    format: YoutubeSabrFormat,
): Long {
    val sequence = runCatching { getMaxSegment(format) }.getOrDefault(0)
    return if (sequence > 0) runCatching { getSegmentEndMs(format, sequence) }.getOrDefault(0L).coerceAtLeast(0L) else 0L
}

internal const val LIVE_EDGE_POLL_MS = 2_000L
internal const val DEFAULT_PLAYBACK_RETRY_MS = 500L
private const val LIVE_TARGET_LATENCY_MS = 10_000L
private const val LIVE_EDGE_TOLERANCE_MS = 15_000L
private const val LIVE_DVR_WINDOW_MS = 12L * 60L * 60L * 1_000L
private const val LIVE_FUTURE_SEGMENT_TOLERANCE = 2
