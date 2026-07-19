package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

internal fun SabrSessionHolder.resolveSegmentDemand(request: SabrSegmentRequest, identity: String): Boolean {
    val requested = session.getCachedSegment(request)
    val rebased = if (requested != null) null else session.findCachedMediaAt(
        format = request.format,
        targetMs = playbackSegmentStartMs(request.format, request.sequenceNumber),
        predictedSequence = request.sequenceNumber,
        allowFollowing = livePlaybackSnapshot()?.active == true,
    )
    val resolved = requested ?: rebased ?: return false
    if (!clearSegmentDemand(request, identity)) return false
    observeMediaSegment(resolved)
    if (rebased != null) session.streamState.jumpBufferedTo(request.format, rebased.header.sequenceNumber)
    return true
}
