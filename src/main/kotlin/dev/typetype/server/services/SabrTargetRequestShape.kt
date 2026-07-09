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
    state.setRequestTrackMode(YoutubeSabrStreamState.TRACK_MODE_VIDEO_AND_AUDIO, true, true)
    state.setSelectVideoFormatBeforeAudio(request.format.isAudio)
    state.setBufferedRangesOverride(
        listOf(
            request.targetRange(holder),
            companion.bufferedRange(holder, holder.companionSequence(companion, targetPlayerTimeMs)),
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

private fun SabrSessionHolder.companionSequence(format: YoutubeSabrFormat, targetTimeMs: Long): Int = cachedSequence(
    format,
    maxOf(
        (playbackStartSequence(format, targetTimeMs) - 1).coerceAtLeast(0),
        lastServedSequence(format) ?: 0,
    ),
)

private fun SabrSessionHolder.cachedSequence(format: YoutubeSabrFormat, baseSequence: Int): Int {
    var sequence = baseSequence
    while (sequence - baseSequence < COMPANION_CACHE_LOOKAHEAD) {
        val nextSequence = sequence + 1
        val request = SabrSegmentRequest.media(format, nextSequence)
        if (session.getCachedSegment(request) == null) return sequence
        sequence = nextSequence
    }
    return sequence
}

private fun SabrSegmentRequest.targetRange(holder: SabrSessionHolder): SabrBufferedRange =
    format.bufferedRange(holder, (sequenceNumber - 1).coerceAtLeast(0))

private fun SabrSegmentRequest.targetPlayerTimeMs(holder: SabrSessionHolder, startMs: Long): Long {
    val playerTimeMs = holder.playerTimeMs()
    val endMs = holder.session.streamState.getSegmentEndMs(format, sequenceNumber).coerceAtLeast(0L)
    if (endMs > startMs && playerTimeMs >= startMs && playerTimeMs < endMs) return playerTimeMs
    if (endMs > startMs + 1L) return minOf(startMs + TARGET_SEGMENT_OFFSET_MS, endMs - 1L)
    return startMs
}

private fun YoutubeSabrFormat.bufferedRange(holder: SabrSessionHolder, bufferedSequence: Int): SabrBufferedRange {
    val endMs = holder.session.streamState.getSegmentEndMs(this, bufferedSequence)
    val durationMs = endMs.takeIf { it > 0L } ?: 1L
    return SabrBufferedRange(
        itag,
        lastModified,
        xtags,
        0L,
        durationMs,
        if (bufferedSequence > 0) 1 else 0,
        bufferedSequence,
        TIMESCALE,
    )
}

private const val SEEK_FORMAT_ORDER_MS = 1_000L
private const val COMPANION_CACHE_LOOKAHEAD = 4
private const val TARGET_SEGMENT_OFFSET_MS = 1_000L
private const val TIMESCALE = 1_000
