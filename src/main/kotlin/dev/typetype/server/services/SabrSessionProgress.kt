package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.markServed(segment: SabrMediaSegment): Unit {
    if (!segment.header.isInitSegment) {
        val format = if (audioFormat.itag == segment.header.itag) audioFormat else videoFormat
        setReaderPosition(format, segment.header.startMs + segment.header.durationMs)
        setLastServedSequence(segment.header.itag, segment.header.sequenceNumber)
        evictCachedSegmentsBefore(readerTailMs() - BACK_BUFFER_MS)
    }
}

internal fun SabrSessionHolder.markServed(segment: CachedSabrSegment): Unit {
    if (!segment.init) {
        val format = if (audioFormat.itag == segment.itag) audioFormat else videoFormat
        setReaderPosition(format, segment.startMs + segment.durationMs)
        setLastServedSequence(segment.itag, segment.sequence)
        evictCachedSegmentsBefore(readerTailMs() - BACK_BUFFER_MS)
    }
}

internal fun SabrSessionHolder.shouldSend(segment: SabrMediaSegment): Boolean {
    if (segment.header.isInitSegment) return true
    val lastSequence = lastServedSequence(formatForItag(segment.header.itag)) ?: return true
    return segment.header.sequenceNumber > lastSequence
}

internal fun SabrSessionHolder.shouldSend(segment: CachedSabrSegment): Boolean {
    if (segment.init) return true
    val lastSequence = lastServedSequence(formatForItag(segment.itag)) ?: return true
    return segment.sequence > lastSequence
}

private fun SabrSessionHolder.formatForItag(itag: Int): YoutubeSabrFormat =
    if (audioFormat.itag == itag) audioFormat else videoFormat

internal fun bothFormatsKnown(holder: SabrSessionHolder): Boolean {
    val state = holder.session.streamState
    return state.getEndSegment(holder.audioFormat) > 0L &&
        state.getEndSegment(holder.videoFormat) > 0L &&
        state.getSegmentEndMs(holder.audioFormat, 1) > 0L &&
        state.getSegmentEndMs(holder.videoFormat, 1) > 0L
}
