package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrBufferedRange

internal fun SabrSessionHolder.applyClientState(bufferedRanges: List<SabrPlaybackBufferedRange>): Unit {
    session.streamState.setStickyResolutionOverride(videoFormat.height.takeIf { it > 0 })
    session.streamState.setWriteLastManualSelectedResolution(true)
    val ranges = bufferedRanges.mapNotNull { it.toSabrBufferedRange(this) }
    session.streamState.setBufferedRangesOverride(ranges.takeIf { it.isNotEmpty() })
}

private fun SabrPlaybackBufferedRange.toSabrBufferedRange(holder: SabrSessionHolder): SabrBufferedRange? {
    val format = holder.info.findFormatByItag(itag) ?: return null
    val start = startMs.coerceAtLeast(0L)
    val end = endMs.coerceAtLeast(start + 1L)
    val startSeq = startSequence ?: holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(format, start)
    val fallbackEndSeq = holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(format, end).coerceAtLeast(startSeq) - 1
    val endSeq = (endSequence ?: fallbackEndSeq).coerceAtLeast(startSeq)
    return SabrBufferedRange(
        format.itag,
        format.lastModified,
        format.xtags,
        start,
        end - start,
        startSeq,
        endSeq,
        BUFFERED_RANGE_TIMESCALE,
    )
}

private const val BUFFERED_RANGE_TIMESCALE = 1000
