package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal inline fun withTargetedRequestShape(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    block: () -> SabrMediaSegment?,
): SabrMediaSegment? {
    val companion = holder.companionFormat(request.format)
    holder.session.streamState.setLastOnlyRange(request.format, true)
    holder.session.streamState.setFullyBuffered(companion, true)
    return try {
        block()
    } finally {
        holder.session.streamState.setFullyBuffered(companion, false)
        holder.session.streamState.setLastOnlyRange(request.format, false)
    }
}

private fun SabrSessionHolder.companionFormat(format: YoutubeSabrFormat): YoutubeSabrFormat =
    if (format.isAudio) videoFormat else audioFormat
