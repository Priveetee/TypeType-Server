package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

internal object SabrWindowSegmentFetcher {
    suspend fun fetch(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
        segmentCache: SabrSegmentCache?,
    ): SabrMediaSegment? {
        val segment = runCatchingNonCancellation {
            holder.session.prepareForMediaSegment(request)
            holder.session.pumpOnceStreamingUntilCached(localization, request)
            holder.session.getCachedSegment(request)
        }.getOrNull()
        if (segment != null) segmentCache?.put(holder, segment)
        return segment?.takeIf { it.matches(request) }
            ?: holder.session.getCachedSegment(request)
    }

    private fun SabrMediaSegment.matches(request: SabrSegmentRequest): Boolean {
        if (header.isInitSegment || request.isInitializationSegment) return false
        return header.itag == request.format.itag && header.sequenceNumber == request.sequenceNumber
    }
}
