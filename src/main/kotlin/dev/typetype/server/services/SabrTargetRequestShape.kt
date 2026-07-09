package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrBufferedRange
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal inline fun <T> withTargetedRequestShape(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    block: () -> T,
): T {
    val companion = holder.companionFormat(request.format)
    val state = holder.session.streamState
    val targetIsAudio = request.format.isAudio
    state.setSelectVideoFormatBeforeAudio(targetIsAudio)
    state.setBufferedRangesOverride(listOf(companion.fullRange(), request.targetRange(holder)))
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

private fun YoutubeSabrFormat.fullRange(): SabrBufferedRange =
    SabrBufferedRange(
        itag,
        lastModified,
        xtags,
        0L,
        MAX_RANGE_DURATION_MS,
        MAX_RANGE_INDEX,
        MAX_RANGE_INDEX,
        TIMESCALE,
    )

private fun SabrSegmentRequest.targetRange(holder: SabrSessionHolder): SabrBufferedRange {
    val state = holder.session.streamState
    val startMs = state.getSegmentStartMs(format, sequenceNumber).coerceAtLeast(0L)
    val endMs = state.getSegmentEndMs(format, sequenceNumber)
    val durationMs = (endMs - startMs).takeIf { it > 0L } ?: 1L
    return SabrBufferedRange(
        format.itag,
        format.lastModified,
        format.xtags,
        0L,
        durationMs,
        sequenceNumber,
        sequenceNumber,
        TIMESCALE,
    )
}

private const val SEEK_FORMAT_ORDER_MS = 1_000L
private const val MAX_RANGE_DURATION_MS = Int.MAX_VALUE.toLong()
private const val MAX_RANGE_INDEX = Int.MAX_VALUE
private const val TIMESCALE = 1_000
