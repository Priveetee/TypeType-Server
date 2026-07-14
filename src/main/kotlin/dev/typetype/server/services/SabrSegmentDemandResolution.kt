package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

internal fun SabrSessionHolder.resolveSegmentDemand(request: SabrSegmentRequest, identity: String): Boolean {
    val requestedCached = session.getCachedSegment(request) != null
    val rebased = if (requestedCached) null else session.findCachedMediaAt(
        format = request.format,
        targetMs = session.streamState.getBufferedEndMs(request.format),
        predictedSequence = request.sequenceNumber,
    )
    val resolved = requestedCached || rebased != null
    if (!resolved || !clearSegmentDemand(request, identity)) return false
    if (rebased != null) session.streamState.jumpBufferedTo(request.format, rebased.header.sequenceNumber)
    return true
}
