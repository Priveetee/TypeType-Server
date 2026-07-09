package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import java.util.concurrent.ConcurrentHashMap

internal object SabrSegmentDemandTracker {
    private val demands = ConcurrentHashMap<String, Pair<SabrSegmentRequest, Long>>()

    fun request(holder: SabrSessionHolder, request: SabrSegmentRequest): Unit {
        if (request.isInitializationSegment) return
        if (holder.session.getCachedSegment(request) != null) return clear(holder, request)
        demands.putIfAbsent(key(holder, request), request to System.currentTimeMillis())
    }

    fun clear(holder: SabrSessionHolder, request: SabrSegmentRequest): Unit {
        demands.remove(key(holder, request))
    }

    fun clear(holder: SabrSessionHolder): Unit {
        val prefix = prefix(holder)
        demands.keys.removeIf { it.startsWith(prefix) }
    }

    fun clearAll(): Unit {
        demands.clear()
    }

    fun next(holder: SabrSessionHolder): SabrSegmentRequest? {
        val prefix = prefix(holder)
        var selected: SabrSegmentRequest? = null
        var selectedSinceMs = Long.MAX_VALUE
        for ((key, value) in demands) {
            if (!key.startsWith(prefix)) continue
            val request = value.first
            if (holder.session.getCachedSegment(request) != null) {
                demands.remove(key)
                continue
            }
            val sinceMs = value.second
            if (sinceMs < selectedSinceMs) {
                selected = request
                selectedSinceMs = sinceMs
            }
        }
        return selected
    }

    fun pendingSummary(holder: SabrSessionHolder): String? = next(holder)?.let { "${it.format.itag}:${it.sequenceNumber}" }

    private fun key(holder: SabrSessionHolder, request: SabrSegmentRequest): String =
        "${prefix(holder)}${request.format.itag}:${request.sequenceNumber}"

    private fun prefix(holder: SabrSessionHolder): String = "${holder.sessionToken}:${holder.activeGeneration()}:"
}

internal fun SabrSessionHolder.requestSegmentDemand(request: SabrSegmentRequest): Unit =
    SabrSegmentDemandTracker.request(this, request)

internal fun SabrSessionHolder.clearSegmentDemand(request: SabrSegmentRequest): Unit =
    SabrSegmentDemandTracker.clear(this, request)

internal fun SabrSessionHolder.clearSegmentDemands(): Unit = SabrSegmentDemandTracker.clear(this)

internal fun SabrSessionHolder.nextSegmentDemand(): SabrSegmentRequest? = SabrSegmentDemandTracker.next(this)

internal fun SabrSessionHolder.pendingSegmentDemandSummary(): String? = SabrSegmentDemandTracker.pendingSummary(this)
