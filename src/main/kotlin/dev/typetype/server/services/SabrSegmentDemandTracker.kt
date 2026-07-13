package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object SabrSegmentDemandTracker {
    private val demands = ConcurrentHashMap<String, SegmentDemand>()
    private val order = AtomicLong()

    fun request(holder: SabrSessionHolder, request: SabrSegmentRequest, nowMs: Long): Unit {
        if (request.isInitializationSegment) return
        if (holder.session.getCachedSegment(request) != null) return clear(holder, request)
        val requestKey = key(holder, request)
        demands.putIfAbsent(requestKey, SegmentDemand(request, order.incrementAndGet(), nowMs))
    }

    fun clear(holder: SabrSessionHolder, request: SabrSegmentRequest): Unit {
        demands.remove(key(holder, request))
    }

    fun clear(holder: SabrSessionHolder): Unit {
        val prefix = holderPrefix(holder)
        demands.keys.removeIf { it.startsWith(prefix) }
    }

    fun clearAll(): Unit {
        demands.clear()
    }

    fun next(holder: SabrSessionHolder): SabrSegmentRequest? {
        val prefix = prefix(holder)
        var selected: SabrSegmentRequest? = null
        var selectedStartMs = Long.MAX_VALUE
        var selectedOrder = Long.MAX_VALUE
        for ((key, value) in demands) {
            if (!key.startsWith(prefix)) continue
            val request = value.request
            if (holder.session.getCachedSegment(request) != null) {
                demands.remove(key)
                continue
            }
            val startMs = holder.session.streamState
                .getSegmentStartMs(request.format, request.sequenceNumber)
                .coerceAtLeast(0L)
            if (startMs < selectedStartMs || startMs == selectedStartMs && value.order < selectedOrder) {
                selected = request
                selectedStartMs = startMs
                selectedOrder = value.order
            }
        }
        return selected
    }

    fun pendingSummary(holder: SabrSessionHolder): String? = next(holder)?.let { "${it.format.itag}:${it.sequenceNumber}" }

    fun takeExpired(holder: SabrSessionHolder, nowMs: Long, deadlineMs: Long): SabrSegmentRequest? {
        val request = next(holder) ?: return null
        val requestKey = key(holder, request)
        val demand = demands[requestKey] ?: return null
        if (nowMs - demand.sinceMs < deadlineMs) return null
        if (holder.session.getCachedSegment(request) != null) {
            demands.remove(requestKey, demand)
            return null
        }
        return request.takeIf { demands.remove(requestKey, demand) }
    }

    private fun key(holder: SabrSessionHolder, request: SabrSegmentRequest): String =
        "${prefix(holder)}${request.format.itag}:${request.sequenceNumber}"

    private fun prefix(holder: SabrSessionHolder): String = "${holderPrefix(holder)}${holder.activeGeneration()}:"

    private fun holderPrefix(holder: SabrSessionHolder): String = "${holder.sessionToken}:"

    private data class SegmentDemand(val request: SabrSegmentRequest, val order: Long, val sinceMs: Long)
}

internal fun SabrSessionHolder.requestSegmentDemand(
    request: SabrSegmentRequest,
    nowMs: Long = System.currentTimeMillis(),
): Unit = SabrSegmentDemandTracker.request(this, request, nowMs)

internal fun SabrSessionHolder.failExpiredSegmentDemand(nowMs: Long = System.currentTimeMillis()): Boolean {
    val request = SabrSegmentDemandTracker.takeExpired(this, nowMs, SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS) ?: return false
    failTerminal("SABR demand stalled for ${request.format.itag}:${request.sequenceNumber}")
    return true
}

internal fun SabrSessionHolder.clearSegmentDemand(request: SabrSegmentRequest): Unit =
    SabrSegmentDemandTracker.clear(this, request)

internal fun SabrSessionHolder.clearSegmentDemands(): Unit = SabrSegmentDemandTracker.clear(this)

internal fun SabrSessionHolder.nextSegmentDemand(): SabrSegmentRequest? = SabrSegmentDemandTracker.next(this)

internal fun SabrSessionHolder.pendingSegmentDemandSummary(): String? = SabrSegmentDemandTracker.pendingSummary(this)
