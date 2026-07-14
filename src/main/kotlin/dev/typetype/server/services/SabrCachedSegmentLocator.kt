package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession

internal fun YoutubeSabrSession.findCachedMediaAt(
    format: YoutubeSabrFormat,
    targetMs: Long,
    predictedSequence: Int,
): SabrMediaSegment? {
    for (distance in 1..MAX_SEQUENCE_DISTANCE) {
        cachedMedia(format, predictedSequence + distance)?.takeIf { it.covers(targetMs) }?.let { return it }
        cachedMedia(format, predictedSequence - distance)?.takeIf { it.covers(targetMs) }?.let { return it }
    }
    return null
}

private fun YoutubeSabrSession.cachedMedia(format: YoutubeSabrFormat, sequence: Int): SabrMediaSegment? {
    if (sequence < 1) return null
    return getCachedSegment(SabrSegmentRequest.media(format, sequence))
}

private fun SabrMediaSegment.covers(targetMs: Long): Boolean {
    val startMs = header.startMs
    return startMs >= 0L &&
        header.durationMs > 0L &&
        targetMs >= startMs - TIMING_TOLERANCE_MS &&
        targetMs < startMs + header.durationMs
}

private const val MAX_SEQUENCE_DISTANCE = 24
private const val TIMING_TOLERANCE_MS = 2L
