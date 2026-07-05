package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrWebSocketLimits
import dev.typetype.server.services.markServed
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment

internal suspend fun DefaultWebSocketServerSession.sendSabrSegments(
    holder: SabrSessionHolder,
    requestId: String?,
    segments: List<SabrMediaSegment>,
): Unit {
    val totalBytes = segments.sumOf { it.length.toLong() }
    send(Frame.Text(SabrWebSocketProtocol.startedJson(requestId, segments.size, totalBytes).toString()))
    for (segment in segments) {
        sendSabrSegment(requestId, segment.length, segment.data, SabrWebSocketProtocol.segmentJson(requestId, segment).toString())
    }
    send(Frame.Text(SabrWebSocketProtocol.doneJson(holder, requestId, segments.size, totalBytes).toString()))
}

internal suspend fun DefaultWebSocketServerSession.sendCachedSabrSegments(
    holder: SabrSessionHolder,
    requestId: String?,
    segments: List<CachedSabrSegment>,
): Unit {
    val totalBytes = segments.sumOf { it.length.toLong() }
    send(Frame.Text(SabrWebSocketProtocol.startedJson(requestId, segments.size, totalBytes).toString()))
    for (segment in segments) {
        holder.markServed(segment)
        sendSabrSegment(requestId, segment.length, segment.bytes, SabrWebSocketProtocol.segmentJson(requestId, segment).toString())
    }
    send(Frame.Text(SabrWebSocketProtocol.doneJson(holder, requestId, segments.size, totalBytes).toString()))
}

private suspend fun DefaultWebSocketServerSession.sendSabrSegment(
    requestId: String?,
    length: Int,
    bytes: ByteArray,
    metadata: String,
): Unit {
    if (length.toLong() > SabrWebSocketLimits.MAX_BINARY_FRAME_BYTES) {
        send(Frame.Text(SabrWebSocketProtocol.errorJson(requestId, "segment_too_large", "SABR segment exceeds WebSocket frame limit").toString()))
        return
    }
    send(Frame.Text(metadata))
    send(Frame.Binary(true, bytes))
}
