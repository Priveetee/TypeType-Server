package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

internal fun SabrSessionHolder.markServed(segment: SabrMediaSegment): Unit {
    if (!segment.header.isInitSegment) {
        val format = if (audioFormat.itag == segment.header.itag) audioFormat else videoFormat
        setReaderPosition(format, segment.header.startMs + segment.header.durationMs)
        setLastServedSequence(segment.header.itag, segment.header.sequenceNumber)
    }
}

internal fun SabrSessionHolder.markRequested(request: SabrSegmentRequest): Unit {
    val startMs = session.streamState.getSegmentStartMs(request.format, request.sequenceNumber)
    setReaderPosition(request.format, startMs.coerceAtLeast(0L))
}

internal fun bothFormatsKnown(holder: SabrSessionHolder): Boolean {
    val state = holder.session.streamState
    return state.getEndSegment(holder.audioFormat) > 0L &&
        state.getEndSegment(holder.videoFormat) > 0L &&
        state.getSegmentEndMs(holder.audioFormat, 1) > 0L &&
        state.getSegmentEndMs(holder.videoFormat, 1) > 0L
}
