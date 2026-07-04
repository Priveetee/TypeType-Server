package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.SabrWebSocketLimits
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment

internal class SabrWebSocketHandler(private val sabrSessionStore: SabrSessionStore) {
    private val json = Json { ignoreUnknownKeys = true }

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
        val requestId = message.sabrString("requestId")
        when (message.sabrString("type")) {
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
        if (!validateSessionItags(session, holder, requestId, message)) return
        holder.pumpMutex.withLock { holder.applySabrWebSocketState(message) }
        session.send(Frame.Text(SabrWebSocketProtocol.stateJson(holder, requestId).toString()))
    }

    private suspend fun handlePump(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        requestId: String?,
        message: JsonObject,
    ): Unit {
        if (message.hasSabrTargetFields()) {
            return sendError(
                session,
                requestId,
                "invalid_pump",
                "SABR pump does not accept sequence targets",
            )
        }
        if (!validateSessionItags(session, holder, requestId, message)) return
        val playerTimeMs = message.sabrLong("playerTimeMs")
        if (playerTimeMs == null) {
            holder.pumpMutex.withLock { holder.applySabrWebSocketState(message) }
            return sendError(
                session,
                requestId,
                "missing_player_time",
                "SABR pump requires playerTimeMs",
            )
        }
        if (!message.hasSabrActiveTrack(holder)) {
            return sendError(
                session,
                requestId,
                "no_active_tracks",
                "SABR pump requires an active audio or video track",
            )
        }
        holder.pumpMutex.withLock { holder.applySabrWebSocketState(message) }
        var completed = false
        val segments = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            sabrSessionStore.fetchMediaAt(holder, playerTimeMs).also { completed = true }
        }
        if (!completed) return sendError(session, requestId, "timeout", "SABR pump timed out")
        if (segments == null) {
            return sendError(session, requestId, "segment_unavailable", "SABR segment unavailable")
        }
        if (segments.isEmpty()) {
            return sendError(session, requestId, "segment_unavailable", "SABR segment unavailable")
        }
        sendSegments(session, holder, requestId, segments)
    }

    private suspend fun handleSegment(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        requestId: String?,
        message: JsonObject,
        init: Boolean,
    ): Unit {
        if (!validateSessionItags(session, holder, requestId, message)) return
        val request = holder.sabrTargetRequest(message, init)
            ?: return sendError(
                session,
                requestId,
                "invalid_target",
                "Missing or invalid SABR target",
            )
        holder.pumpMutex.withLock { holder.applySabrWebSocketState(message) }
        var completed = false
        val segment = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            sabrSessionStore.fetchSegment(holder, request).also { completed = true }
        }
        if (!completed) {
            return sendError(session, requestId, "timeout", "SABR segment fetch timed out")
        }
        if (segment == null) {
            return sendError(session, requestId, "segment_unavailable", "SABR segment unavailable")
        }
        sendSegments(session, holder, requestId, listOf(segment))
    }

    private suspend fun sendSegments(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        requestId: String?,
        segments: List<SabrMediaSegment>,
    ): Unit {
        val totalBytes = segments.sumOf { it.length.toLong() }
        session.send(
            Frame.Text(SabrWebSocketProtocol.startedJson(requestId, segments.size, totalBytes).toString())
        )
        for (segment in segments) {
            if (segment.length.toLong() > SabrWebSocketLimits.MAX_BINARY_FRAME_BYTES) {
                sendError(session, requestId, "segment_too_large", "SABR segment exceeds WebSocket frame limit")
                return
            }
            session.send(Frame.Text(SabrWebSocketProtocol.segmentJson(requestId, segment).toString()))
            session.send(Frame.Binary(true, segment.data))
        }
        session.send(
            Frame.Text(SabrWebSocketProtocol.doneJson(holder, requestId, segments.size, totalBytes).toString())
        )
    }

    private suspend fun sendError(
        session: DefaultWebSocketServerSession,
        requestId: String?,
        code: String,
        message: String,
    ): Unit {
        session.send(Frame.Text(SabrWebSocketProtocol.errorJson(requestId, code, message).toString()))
    }

    private suspend fun validateSessionItags(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        requestId: String?,
        message: JsonObject,
    ): Boolean = when (val validation = holder.validateSabrWebSocketItags(message)) {
        SabrWebSocketItagValidation.Valid -> true
        is SabrWebSocketItagValidation.Invalid -> {
            sendError(session, requestId, validation.code, validation.message)
            false
        }
    }

    private companion object {
        const val FETCH_TIMEOUT_MS = 20_000L
    }
}
