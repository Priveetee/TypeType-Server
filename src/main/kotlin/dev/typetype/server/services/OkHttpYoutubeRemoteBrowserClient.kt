package dev.typetype.server.services

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class OkHttpYoutubeRemoteBrowserClient(
    private val serviceUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) : YoutubeRemoteBrowserClient {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun start(request: YoutubeRemoteBrowserTokenStartRequest, internalToken: String): YoutubeRemoteBrowserTokenStartResponse? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url("${serviceUrl.trimEnd('/')}/youtube-remote-login/start")
            .header(INTERNAL_HEADER, internalToken)
            .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        runCatching {
            client.newCall(httpRequest).execute().use(::decodeStartResponse)
        }.getOrNull()
    }

    override suspend fun cancel(tokenSessionId: String, internalToken: String): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val request = Request.Builder()
                .url("${serviceUrl.trimEnd('/')}/youtube-remote-login/$tokenSessionId")
                .header(INTERNAL_HEADER, internalToken)
                .delete()
                .build()
            runCatching { client.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
        }

    override suspend fun bridge(
        serverSession: DefaultWebSocketServerSession,
        tokenSessionId: String,
        internalToken: String,
        config: YoutubeRemoteBrowserConfig,
    ): Unit = coroutineScope {
        val done = CompletableDeferred<Unit>()
        val outbound = Channel<Frame>(config.outboundQueueSize, BufferOverflow.DROP_OLDEST)
        val socket = client.newWebSocket(tokenWebSocketRequest(tokenSessionId, internalToken), listener(outbound, done, config))
        val outboundJob = launch { for (frame in outbound) serverSession.send(frame) }
        val inboundJob = launch {
            for (frame in serverSession.incoming) {
                if (frame is Frame.Text) {
                    YoutubeRemoteBrowserMessageGuard.frontendText(frame.readText(), config.maxInputBytes)?.let(socket::send)
                }
                if (frame is Frame.Close) done.complete(Unit)
            }
        }
        outboundJob.invokeOnCompletion { done.complete(Unit) }
        inboundJob.invokeOnCompletion { done.complete(Unit) }
        done.await()
        socket.close(NORMAL_CLOSE, null)
        outbound.close()
        outboundJob.cancel()
        inboundJob.cancel()
    }

    private fun decodeStartResponse(response: Response): YoutubeRemoteBrowserTokenStartResponse? {
        if (!response.isSuccessful) return null
        return json.decodeFromString<YoutubeRemoteBrowserTokenStartResponse>(response.body.string())
    }

    private fun tokenWebSocketRequest(tokenSessionId: String, internalToken: String): Request =
        Request.Builder()
            .url("${webSocketBaseUrl()}/youtube-remote-login/$tokenSessionId")
            .header(INTERNAL_HEADER, internalToken)
            .build()

    private fun listener(
        outbound: Channel<Frame>,
        done: CompletableDeferred<Unit>,
        config: YoutubeRemoteBrowserConfig,
    ): WebSocketListener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            YoutubeRemoteBrowserMessageGuard.tokenText(text)?.let { outbound.trySend(Frame.Text(it)) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (bytes.size <= config.maxFrameBytes) outbound.trySend(Frame.Binary(true, bytes.toByteArray()))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            done.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            done.complete(Unit)
        }
    }

    private fun webSocketBaseUrl(): String =
        serviceUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")

    companion object {
        private const val INTERNAL_HEADER = "X-Internal-Token"
        private const val NORMAL_CLOSE = 1000
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
