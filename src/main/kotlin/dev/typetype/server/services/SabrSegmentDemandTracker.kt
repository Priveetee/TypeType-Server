package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object SabrSegmentDemandTracker {
    private val demands = ConcurrentHashMap<String, SegmentDemand>()
    private val order = AtomicLong()

    fun request(holder: SabrSessionHolder, request: SabrSegmentRequest): Unit {
        if (request.isInitializationSegment) return
        if (holder.session.getCachedSegment(request) != null) return clear(holder, request)
        val prefix = prefix(holder)
        val requestKey = key(holder, request)
        demands.keys.removeIf { it.startsWith(prefix) && it != requestKey }
        demands.putIfAbsent(requestKey, SegmentDemand(request, order.incrementAndGet()))
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
        var selectedOrder = Long.MAX_VALUE
        for ((key, value) in demands) {
            if (!key.startsWith(prefix)) continue
            val request = value.request
            if (holder.session.getCachedSegment(request) != null) {
                demands.remove(key)
                continue
            }
            if (value.order < selectedOrder) {
                selected = request
                selectedOrder = value.order
            }
        }
        return selected
    }

    fun pendingSummary(holder: SabrSessionHolder): String? = next(holder)?.let { "${it.format.itag}:${it.sequenceNumber}" }

    private fun key(holder: SabrSessionHolder, request: SabrSegmentRequest): String =
        "${prefix(holder)}${request.format.itag}:${request.sequenceNumber}"

    private fun prefix(holder: SabrSessionHolder): String = "${holder.sessionToken}:${holder.activeGeneration()}:"

    private data class SegmentDemand(val request: SabrSegmentRequest, val order: Long)
}

internal fun SabrSessionHolder.requestSegmentDemand(request: SabrSegmentRequest): Unit =
    SabrSegmentDemandTracker.request(this, request)

internal fun SabrSessionHolder.clearSegmentDemand(request: SabrSegmentRequest): Unit =
    SabrSegmentDemandTracker.clear(this, request)

internal fun SabrSessionHolder.clearSegmentDemands(): Unit = SabrSegmentDemandTracker.clear(this)

internal fun SabrSessionHolder.nextSegmentDemand(): SabrSegmentRequest? = SabrSegmentDemandTracker.next(this)

internal fun SabrSessionHolder.pendingSegmentDemandSummary(): String? = SabrSegmentDemandTracker.pendingSummary(this)
