package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrBufferedRange
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal inline fun <T> withLiveContinuationRequestShape(
    holder: SabrSessionHolder,
    block: () -> T,
): T {
    val ranges = buildList {
        holder.observedRange(holder.audioFormat)?.takeIf { holder.isAudioActive() }?.let(::add)
        holder.observedRange(holder.videoFormat)?.takeIf { holder.isVideoActive() }?.let(::add)
    }
    if (ranges.isEmpty()) return block()
    val state = holder.session.streamState
    state.setPlayerTimeMs(holder.playerTimeMs())
    state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
    state.setBufferedRangesOverride(ranges)
    return try {
        block()
    } finally {
        state.setBufferedRangesOverride(null)
        state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
    }
}

private fun SabrSessionHolder.observedRange(format: YoutubeSabrFormat): SabrBufferedRange? {
    val header = observedMediaSegment(format)?.header ?: return null
    val sequence = header.sequenceNumber.takeIf { it > 0 } ?: return null
    val startMs = header.startMs.takeIf { it >= 0L } ?: return null
    val durationMs = header.durationMs.takeIf { it > 0L }
        ?: playbackSegmentDurationMs(format, sequence)
    val bufferedEndMs = startMs + durationMs
    return SabrBufferedRange(
        format.itag,
        format.lastModified,
        format.xtags,
        0L,
        bufferedEndMs,
        1,
        sequence,
        TIMESCALE,
    )
}

private const val TIMESCALE = 1_000
