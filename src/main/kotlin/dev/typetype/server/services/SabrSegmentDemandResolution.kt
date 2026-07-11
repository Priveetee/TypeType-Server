package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

internal fun SabrSessionHolder.resolveSegmentDemand(request: SabrSegmentRequest): Boolean {
    val requestedCached = session.getCachedSegment(request) != null
    if (!requestedCached && key.purpose == SabrSessionPurpose.DOWNLOAD) return false
    val rebased = if (requestedCached) null else session.findCachedMediaAt(
        format = request.format,
        targetMs = session.streamState.getBufferedEndMs(request.format),
        predictedSequence = request.sequenceNumber,
    )
    if (rebased != null) {
        session.streamState.jumpBufferedTo(request.format, rebased.header.sequenceNumber)
    }
    if (requestedCached || rebased != null) clearSegmentDemand(request)
    return requestedCached
}
