package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.SabrWebSocketLimits
import dev.typetype.server.services.clearTargetRequest
import dev.typetype.server.services.configureTargetRequest
import dev.typetype.server.services.runCatchingNonCancellation
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class SabrWebSocketHandler(private val sabrSessionStore: SabrSessionStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val localization = Localization("en", "GB")

    suspend fun handle(session: DefaultWebSocketServerSession, videoId: String): Unit {
        val token = session.call.request.queryParameters["session"]
        val holder = token?.let { sabrSessionStore.lookupByToken(videoId, it) }
        if (holder == null) {
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No active SABR session"))
            return
        }
        holder.retainWebSocket()
        try {
            session.send(Frame.Text(SabrWebSocketProtocol.readyJson(holder).toString()))
            for (frame in session.incoming) {
                if (frame is Frame.Text) handleText(session, holder, frame.readText())
                if (frame is Frame.Close) return
            }
        } finally {
            holder.releaseWebSocket()
        }
    }

    private suspend fun handleText(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        text: String,
    ): Unit {
        holder.touch()
        val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return sendError(session, null, "invalid_json", "Invalid JSON")
        val requestId = message.string("requestId")
        when (message.string("type")) {
            "state" -> handleState(session, holder, requestId, message)
            "pump" -> handlePump(session, holder, requestId, message)
            "segment" -> handleSegment(session, holder, requestId, message, init = false)
            "init" -> handleSegment(session, holder, requestId, message, init = true)
            else -> sendError(session, requestId, "unknown_type", "Unknown SABR WebSocket message type")
        }
    }

    private suspend fun handleState(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        requestId: String?,
        message: JsonObject,
    ): Unit {
        holder.pumpMutex.withLock { applyState(holder, message) }
        session.send(Frame.Text(SabrWebSocketProtocol.stateJson(holder, requestId).toString()))
    }

    private suspend fun handlePump(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        requestId: String?,
        message: JsonObject,
    ): Unit {
        val target = targetRequest(holder, message, init = false)
        var error: Throwable? = null
        val segments = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            holder.pumpMutex.withLock {
                runCatchingNonCancellation {
                    applyState(holder, message)
                    if (target != null) holder.session.configureTargetRequest(holder, target)
                    try {
                        holder.session.pumpOnce(localization)
                    } finally {
                        if (target != null) holder.session.clearTargetRequest(holder)
                    }
                }.onFailure { error = it }.getOrDefault(emptyList())
            }
        }
        val failure = error
        if (segments == null) return sendError(session, requestId, "timeout", "SABR pump timed out")
        if (failure != null) return sendError(session, requestId, "pump_failed", failure.message ?: "SABR pump failed")
        sendSegments(session, holder, requestId, segments)
    }

    private suspend fun handleSegment(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        requestId: String?,
        message: JsonObject,
        init: Boolean,
    ): Unit {
        val request = targetRequest(holder, message, init)
            ?: return sendError(session, requestId, "invalid_target", "Missing or invalid SABR target")
        holder.pumpMutex.withLock { applyState(holder, message) }
        var completed = false
        val segment = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            sabrSessionStore.fetchSegment(holder, request).also { completed = true }
        }
        if (!completed) return sendError(session, requestId, "timeout", "SABR segment fetch timed out")
        if (segment == null) return sendError(session, requestId, "segment_unavailable", "SABR segment unavailable")
        sendSegments(session, holder, requestId, listOf(segment))
    }

    private fun applyState(holder: SabrSessionHolder, message: JsonObject): Unit {
        val audioActive = message.boolean("audioActive") ?: holder.isAudioActive()
        val videoActive = message.boolean("videoActive") ?: holder.isVideoActive()
        holder.setActiveTracks(videoActive = videoActive, audioActive = audioActive)
        message.long("playerTimeMs")?.let {
            holder.setPlayerTimeMs(it)
            holder.session.streamState.setPlayerTimeMs(it)
        }
        message.float("playbackRate")?.let { holder.session.streamState.setPlaybackRate(it) }
    }

    private fun targetRequest(holder: SabrSessionHolder, message: JsonObject, init: Boolean): SabrSegmentRequest? {
        val format = message.int("itag")?.let { formatForItag(holder, it) } ?: return null
        if (init) return SabrSegmentRequest.initialization(format)
        val sequence = message.int("sequence")?.takeIf { it > 0 } ?: return null
        return SabrSegmentRequest.media(format, sequence)
    }

    private suspend fun sendSegments(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        requestId: String?,
        segments: List<SabrMediaSegment>,
    ): Unit {
        val totalBytes = segments.sumOf { it.length.toLong() }
        session.send(Frame.Text(SabrWebSocketProtocol.startedJson(requestId, segments.size, totalBytes).toString()))
        for (segment in segments) {
            if (segment.length.toLong() > SabrWebSocketLimits.MAX_BINARY_FRAME_BYTES) {
                sendError(session, requestId, "segment_too_large", "SABR segment exceeds WebSocket frame limit")
                return
            }
            session.send(Frame.Text(SabrWebSocketProtocol.segmentJson(requestId, segment).toString()))
            session.send(Frame.Binary(true, segment.data))
        }
        session.send(Frame.Text(SabrWebSocketProtocol.doneJson(holder, requestId, segments.size, totalBytes).toString()))
    }

    private suspend fun sendError(
        session: DefaultWebSocketServerSession,
        requestId: String?,
        code: String,
        message: String,
    ): Unit {
        session.send(Frame.Text(SabrWebSocketProtocol.errorJson(requestId, code, message).toString()))
    }

    private fun formatForItag(holder: SabrSessionHolder, itag: Int): YoutubeSabrFormat? = when (itag) {
        holder.audioFormat.itag -> holder.audioFormat
        holder.videoFormat.itag -> holder.videoFormat
        else -> null
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.content
    private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull
    private fun JsonObject.float(name: String): Float? = this[name]?.jsonPrimitive?.doubleOrNull?.toFloat()

    private companion object {
        const val FETCH_TIMEOUT_MS = 20_000L
    }
}
