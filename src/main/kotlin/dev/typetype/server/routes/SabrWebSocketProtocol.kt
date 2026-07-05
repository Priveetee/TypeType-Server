package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrWebSocketLimits
import dev.typetype.server.services.CachedSabrSegment
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment

internal object SabrWebSocketProtocol {
    const val PROTOCOL = "typetype-sabr-ws-v1"

    fun readyJson(holder: SabrSessionHolder): JsonObject = buildJsonObject {
        put("type", "ready")
        put("protocol", PROTOCOL)
        put("videoId", holder.info.videoId)
        put("session", holder.sessionToken)
        put("transport", "stateful-websocket")
        put("maxBinaryFrameBytes", SabrWebSocketLimits.MAX_BINARY_FRAME_BYTES)
        put("binary", "segment metadata text frame followed by binary media frame")
    }

    fun stateJson(holder: SabrSessionHolder, requestId: String?): JsonObject = buildJsonObject {
        put("type", "state")
        requestId?.let { put("requestId", it) }
        put("requestNumber", holder.session.requestNumber)
        put("minBufferedEndMs", holder.session.streamState.getMinBufferedEndMs())
        put("readerHeadMs", holder.readerHeadMs())
        put("readerTailMs", holder.readerTailMs())
        put("cachedBytes", holder.session.cachedBytes)
    }

    fun startedJson(requestId: String?, count: Int, bytes: Long): JsonObject = buildJsonObject {
        put("type", "started")
        requestId?.let { put("requestId", it) }
        put("segmentCount", count)
        put("totalBytes", bytes)
    }

    fun segmentJson(requestId: String?, segment: SabrMediaSegment): JsonObject = buildJsonObject {
        val header = segment.header
        put("type", "segment")
        requestId?.let { put("requestId", it) }
        put("itag", header.itag)
        put("sequence", header.sequenceNumber)
        put("init", header.isInitSegment)
        put("startMs", header.startMs)
        put("durationMs", header.durationMs)
        put("length", segment.length)
    }

    fun segmentJson(requestId: String?, segment: CachedSabrSegment): JsonObject = buildJsonObject {
        put("type", "segment")
        requestId?.let { put("requestId", it) }
        put("itag", segment.itag)
        put("sequence", segment.sequence)
        put("init", segment.init)
        put("startMs", segment.startMs)
        put("durationMs", segment.durationMs)
        put("length", segment.length)
    }

    fun initSegmentJson(requestId: String?, itag: Int, length: Int): JsonObject = buildJsonObject {
        put("type", "segment")
        requestId?.let { put("requestId", it) }
        put("itag", itag)
        put("sequence", -1)
        put("init", true)
        put("startMs", -1)
        put("durationMs", -1)
        put("length", length)
    }

    fun doneJson(
        holder: SabrSessionHolder,
        requestId: String?,
        count: Int,
        bytes: Long,
    ): JsonObject = buildJsonObject {
        put("type", "done")
        requestId?.let { put("requestId", it) }
        put("segmentCount", count)
        put("totalBytes", bytes)
        put("requestNumber", holder.session.requestNumber)
        put("readerHeadMs", holder.readerHeadMs())
        put("readerTailMs", holder.readerTailMs())
        put("cachedBytes", holder.session.cachedBytes)
    }

    fun errorJson(requestId: String?, code: String, message: String): JsonObject = buildJsonObject {
        put("type", "error")
        requestId?.let { put("requestId", it) }
        put("code", code)
        put("message", message)
    }
}
