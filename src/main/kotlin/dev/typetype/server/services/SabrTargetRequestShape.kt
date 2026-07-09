package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

internal inline fun <T> withTargetedRequestShape(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    block: () -> T,
): T {
    val state = holder.session.streamState
    val requestStartMs = state.getSegmentStartMs(request.format, request.sequenceNumber).coerceAtLeast(0L)
    val targetPlayerTimeMs = request.targetPlayerTimeMs(holder, requestStartMs)
    state.setPlayerTimeMs(targetPlayerTimeMs)
    state.setRequestTrackMode(request.trackMode(), true, true)
    state.setSelectVideoFormatBeforeAudio(request.format.isAudio)
    state.setBufferedRangesOverride(emptyList())
    return try {
        block()
    } finally {
        state.setBufferedRangesOverride(null)
        state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
        state.setSelectVideoFormatBeforeAudio(holder.playerTimeMs() > SEEK_FORMAT_ORDER_MS)
    }
}

private fun SabrSegmentRequest.trackMode(): Int =
    if (format.isAudio) YoutubeSabrStreamState.TRACK_MODE_AUDIO_ONLY else YoutubeSabrStreamState.TRACK_MODE_VIDEO_ONLY

private fun SabrSegmentRequest.targetPlayerTimeMs(holder: SabrSessionHolder, startMs: Long): Long {
    val playerTimeMs = holder.playerTimeMs()
    val endMs = holder.session.streamState.getSegmentEndMs(format, sequenceNumber).coerceAtLeast(0L)
    if (endMs > startMs && playerTimeMs >= startMs && playerTimeMs < endMs) return playerTimeMs
    if (endMs > startMs + 1L) return minOf(startMs + TARGET_SEGMENT_OFFSET_MS, endMs - 1L)
    return startMs
}

private const val SEEK_FORMAT_ORDER_MS = 1_000L
private const val TARGET_SEGMENT_OFFSET_MS = 1_000L
