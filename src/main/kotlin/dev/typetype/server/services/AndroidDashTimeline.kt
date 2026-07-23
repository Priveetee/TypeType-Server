package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

internal data class AndroidDashTimeline(
    val startNumber: Int,
    val segments: List<AndroidDashTimelineSegment>,
) {
    val endMs: Long get() = segments.last().endMs
}

internal data class AndroidDashTimelineSegment(
    val startMs: Long,
    val durationMs: Long,
) {
    val endMs: Long get() = startMs + durationMs
}

internal sealed interface AndroidDashTimelineResult {
    data class Ready(val timeline: AndroidDashTimeline) : AndroidDashTimelineResult
    data object Pending : AndroidDashTimelineResult
    data class Invalid(val reason: String) : AndroidDashTimelineResult
}

internal object AndroidDashTimelineReader {
    fun read(
        state: YoutubeSabrStreamState,
        format: YoutubeSabrFormat,
    ): AndroidDashTimelineResult {
        if (!state.hasSegmentIndex(format)) return AndroidDashTimelineResult.Pending
        val endSegment = state.getEndSegment(format)
        if (endSegment !in 1..MAX_SEGMENTS) {
            return AndroidDashTimelineResult.Invalid("Invalid exact segment count for itag ${format.itag}")
        }
        val segments = ArrayList<AndroidDashTimelineSegment>(endSegment.toInt())
        for (sequence in 1..endSegment.toInt()) {
            val startMs = state.getSegmentStartMs(format, sequence)
            val endMs = state.getSegmentEndMs(format, sequence)
            if (startMs < 0L || endMs <= startMs) {
                return AndroidDashTimelineResult.Invalid("Invalid exact timeline for itag ${format.itag}")
            }
            if (sequence == 1 && startMs != 0L) {
                return AndroidDashTimelineResult.Invalid("Timeline does not begin at zero for itag ${format.itag}")
            }
            if (segments.lastOrNull()?.startMs?.let { startMs <= it } == true) {
                return AndroidDashTimelineResult.Invalid("Timeline is not monotonic for itag ${format.itag}")
            }
            segments += AndroidDashTimelineSegment(startMs, endMs - startMs)
        }
        return AndroidDashTimelineResult.Ready(AndroidDashTimeline(startNumber = 1, segments))
    }

    private const val MAX_SEGMENTS = 100_000L
}
