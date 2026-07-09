package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrBufferedRange
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

internal inline fun <T> withTargetedRequestShape(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    block: () -> T,
): T {
    val companion = holder.companionFormat(request.format)
    val state = holder.session.streamState
    val requestStartMs = state.getSegmentStartMs(request.format, request.sequenceNumber).coerceAtLeast(0L)
    val targetPlayerTimeMs = request.targetPlayerTimeMs(holder, requestStartMs)
    state.setPlayerTimeMs(targetPlayerTimeMs)
    state.setRequestTrackMode(request.trackMode(), request.format.isAudio, request.format.isVideo)
    state.setSelectVideoFormatBeforeAudio(request.format.isAudio)
    state.setBufferedRangesOverride(
        listOf(
            request.targetRange(holder),
            companion.fullRange(),
        )
    )
    return try {
        block()
    } finally {
        state.setBufferedRangesOverride(null)
        state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
        state.setSelectVideoFormatBeforeAudio(holder.playerTimeMs() > SEEK_FORMAT_ORDER_MS)
    }
}

private fun SabrSessionHolder.companionFormat(format: YoutubeSabrFormat): YoutubeSabrFormat =
    if (format.isAudio) videoFormat else audioFormat

private fun SabrSegmentRequest.trackMode(): Int =
    if (format.isAudio) YoutubeSabrStreamState.TRACK_MODE_AUDIO_ONLY else YoutubeSabrStreamState.TRACK_MODE_VIDEO_ONLY

private fun SabrSegmentRequest.targetRange(holder: SabrSessionHolder): SabrBufferedRange =
    format.bufferedRange(holder, sequenceNumber)

private fun SabrSegmentRequest.targetPlayerTimeMs(holder: SabrSessionHolder, startMs: Long): Long {
    val playerTimeMs = holder.playerTimeMs()
    val endMs = holder.session.streamState.getSegmentEndMs(format, sequenceNumber).coerceAtLeast(0L)
    if (endMs > startMs && playerTimeMs >= startMs && playerTimeMs < endMs) return playerTimeMs
    if (endMs > startMs + 1L) return minOf(startMs + TARGET_SEGMENT_OFFSET_MS, endMs - 1L)
    return startMs
}

private fun YoutubeSabrFormat.bufferedRange(holder: SabrSessionHolder, bufferedSequence: Int): SabrBufferedRange {
    val startMs = holder.session.streamState.getSegmentStartMs(this, bufferedSequence).coerceAtLeast(0L)
    val endMs = holder.session.streamState.getSegmentEndMs(this, bufferedSequence)
    val durationMs = (endMs - startMs).takeIf { it > 0L } ?: 1L
    return SabrBufferedRange(
        itag,
        lastModified,
        xtags,
        startMs,
        durationMs,
        bufferedSequence,
        bufferedSequence,
        TIMESCALE,
    )
}

private fun YoutubeSabrFormat.fullRange(): SabrBufferedRange = SabrBufferedRange(
    itag,
    lastModified,
    xtags,
    0L,
    Int.MAX_VALUE.toLong(),
    Int.MAX_VALUE,
    Int.MAX_VALUE,
    TIMESCALE,
)

private const val SEEK_FORMAT_ORDER_MS = 1_000L
private const val TARGET_SEGMENT_OFFSET_MS = 1_000L
private const val TIMESCALE = 1_000
