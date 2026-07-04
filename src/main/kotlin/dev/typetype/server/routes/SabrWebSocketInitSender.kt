package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

internal object SabrWebSocketInitSender {
    suspend fun send(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        sabrSessionStore: SabrSessionStore,
        requestId: String?,
        request: SabrSegmentRequest,
        timeoutMs: Long,
    ): Unit = send(session, holder, sabrSessionStore, requestId, request.format.itag, timeoutMs)

    suspend fun send(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        sabrSessionStore: SabrSessionStore,
        requestId: String?,
        itag: Int,
        timeoutMs: Long,
    ): Unit {
        val format = if (holder.audioFormat.itag == itag) holder.audioFormat else holder.videoFormat
        var completed = false
        val bytes = withTimeoutOrNull(timeoutMs) {
            sabrSessionStore.fetchInitializationData(holder, format).also { completed = true }
        }
        if (!completed) return sendError(session, requestId, "timeout", "SABR init fetch timed out")
        if (bytes == null) {
            return sendError(session, requestId, "segment_unavailable", "SABR segment unavailable")
        }
        sendBytes(session, holder, requestId, itag, bytes)
    }

    private suspend fun sendBytes(
        session: DefaultWebSocketServerSession,
        holder: SabrSessionHolder,
        requestId: String?,
        itag: Int,
        bytes: ByteArray,
    ): Unit {
        val length = bytes.size.toLong()
        session.send(Frame.Text(SabrWebSocketProtocol.startedJson(requestId, 1, length).toString()))
        session.send(Frame.Text(SabrWebSocketProtocol.initSegmentJson(requestId, itag, bytes.size).toString()))
        session.send(Frame.Binary(true, bytes))
        session.send(Frame.Text(SabrWebSocketProtocol.doneJson(holder, requestId, 1, length).toString()))
    }

    private suspend fun sendError(
        session: DefaultWebSocketServerSession,
        requestId: String?,
        code: String,
        message: String,
    ): Unit {
        session.send(Frame.Text(SabrWebSocketProtocol.errorJson(requestId, code, message).toString()))
    }
}
