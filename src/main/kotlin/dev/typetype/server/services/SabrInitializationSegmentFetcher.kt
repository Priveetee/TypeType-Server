package dev.typetype.server.services

import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

internal suspend fun fetchSabrInitializationSegment(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    localization: Localization,
    segmentCache: SabrSegmentCache?,
    markServed: Boolean,
): SabrMediaSegment? = holder.pumpMutex.withLock {
    holder.session.getCachedSegment(request)?.let { segment ->
        if (markServed) holder.markServed(segment)
        segmentCache?.put(holder, segment)
        return@withLock segment
    }
    if (holder.session.isBeyondEnd(request)) return@withLock null
    runCatchingNonCancellation {
        holder.session.prepareForInitialization(request.format)
        holder.session.pumpOnceStreamingUntilCached(localization, request)
        holder.session.getCachedSegment(request)
    }
        .getOrNull()
        ?.also {
            if (markServed) holder.markServed(it)
            segmentCache?.put(holder, it)
        }
}
