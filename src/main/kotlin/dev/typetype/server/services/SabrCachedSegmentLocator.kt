package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession

internal fun YoutubeSabrSession.findCachedMediaAt(
    format: YoutubeSabrFormat,
    targetMs: Long,
    predictedSequence: Int,
    fallbackDurationMs: Long? = null,
    allowFollowing: Boolean = false,
): SabrMediaSegment? {
    for (distance in 1..MAX_SEQUENCE_DISTANCE) {
        cachedMedia(format, predictedSequence + distance)
            ?.takeIf { it.covers(targetMs, fallbackDurationMs) }
            ?.let { return it }
        cachedMedia(format, predictedSequence - distance)
            ?.takeIf { it.covers(targetMs, fallbackDurationMs) }
            ?.let { return it }
    }
    if (!allowFollowing) return null
    for (distance in 1..MAX_SEQUENCE_DISTANCE) {
        cachedMedia(format, predictedSequence + distance)
            ?.takeIf { it.startsWithinNextSegment(targetMs, fallbackDurationMs) }
            ?.let { return it }
    }
    return null
}

private fun YoutubeSabrSession.cachedMedia(format: YoutubeSabrFormat, sequence: Int): SabrMediaSegment? {
    if (sequence < 1) return null
    return getCachedSegment(SabrSegmentRequest.media(format, sequence))
}

private fun SabrMediaSegment.covers(targetMs: Long, fallbackDurationMs: Long?): Boolean {
    val startMs = header.startMs
    val durationMs = header.durationMs.takeIf { it > 0L } ?: fallbackDurationMs ?: return false
    return startMs >= 0L &&
        targetMs >= startMs - TIMING_TOLERANCE_MS &&
        targetMs < startMs + durationMs
}

private fun SabrMediaSegment.startsWithinNextSegment(targetMs: Long, fallbackDurationMs: Long?): Boolean {
    val durationMs = header.durationMs.takeIf { it > 0L } ?: fallbackDurationMs ?: return false
    val leadMs = header.startMs - targetMs
    return header.startMs >= 0L && leadMs in -TIMING_TOLERANCE_MS..durationMs
}

private const val MAX_SEQUENCE_DISTANCE = 24
private const val TIMING_TOLERANCE_MS = 2L
