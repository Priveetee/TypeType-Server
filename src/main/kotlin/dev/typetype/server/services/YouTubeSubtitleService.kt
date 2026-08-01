package dev.typetype.server.services

import dev.typetype.server.REQUEST_ID_HEADER
import dev.typetype.server.cache.CacheJson
import dev.typetype.server.currentRequestId
import dev.typetype.server.models.SubtitleItem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

internal class YouTubeSubtitleService(private val httpClient: OkHttpClient, private val baseUrl: String) {

    suspend fun fetchSubtitles(videoId: String): List<SubtitleItem> =
        when (val result = fetchSubtitleInventory(videoId)) {
            is YouTubeSubtitleInventoryResult.Ready -> result.tracks
            YouTubeSubtitleInventoryResult.Unavailable -> emptyList()
        }

    suspend fun fetchSubtitleInventory(videoId: String): YouTubeSubtitleInventoryResult {
        val requestId = currentRequestId()
        return suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url("$baseUrl/subtitles?videoId=$videoId")
                .apply { requestId?.let { header(REQUEST_ID_HEADER, it) } }
                .build()
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(YouTubeSubtitleInventoryResult.Unavailable)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        if (!it.isSuccessful) {
                            YouTubeSubtitleInventoryResult.Unavailable
                        } else {
                            runCatching<YouTubeSubtitleInventoryResult> {
                                YouTubeSubtitleInventoryResult.Ready(
                                    CacheJson.decodeFromString<List<SubtitleItem>>(it.body.string()),
                                )
                            }.getOrDefault(YouTubeSubtitleInventoryResult.Unavailable)
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

    suspend fun fetchSubtitleContent(url: String): YouTubeSubtitleContentResult {
        val tokenUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("subtitles/content")
            .addQueryParameter("url", url)
            .build()
        return execute(Request.Builder().url(tokenUrl).build()) { response ->
            if (response.isSuccessful) {
                val body = response.body
                val declaredSize = body.contentLength()
                if (declaredSize > MAX_SUBTITLE_BYTES) return@execute YouTubeSubtitleContentResult.InvalidPayload
                val bytes = body.byteStream().readNBytes(MAX_SUBTITLE_BYTES + 1)
                if (bytes.size > MAX_SUBTITLE_BYTES || !bytes.isWebVtt()) {
                    YouTubeSubtitleContentResult.InvalidPayload
                } else {
                    YouTubeSubtitleContentResult.Ready(bytes)
                }
            } else {
                val error = runCatching {
                    CacheJson.decodeFromString<TokenSubtitleError>(response.body.string())
                }.getOrNull()
                when (error?.code) {
                    "subtitle_request_invalid" -> YouTubeSubtitleContentResult.InvalidRequest
                    "subtitle_track_not_found" -> YouTubeSubtitleContentResult.NotFound
                    "subtitle_upstream_throttled" -> YouTubeSubtitleContentResult.Throttled
                    "subtitle_payload_invalid" -> YouTubeSubtitleContentResult.InvalidPayload
                    else -> YouTubeSubtitleContentResult.Unavailable
                }
            }
        }
    }

    private suspend fun execute(
        request: Request,
        readResponse: (Response) -> YouTubeSubtitleContentResult,
    ): YouTubeSubtitleContentResult {
        val requestId = currentRequestId()
        val observedRequest = request.newBuilder()
            .apply { requestId?.let { header(REQUEST_ID_HEADER, it) } }
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(observedRequest)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(YouTubeSubtitleContentResult.Unavailable)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        runCatching { readResponse(it) }.getOrDefault(YouTubeSubtitleContentResult.Unavailable)
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

    private companion object {
        const val MAX_SUBTITLE_BYTES = 5 * 1024 * 1024
    }
}

internal sealed interface YouTubeSubtitleInventoryResult {
    data class Ready(val tracks: List<SubtitleItem>) : YouTubeSubtitleInventoryResult
    data object Unavailable : YouTubeSubtitleInventoryResult
}

internal sealed interface YouTubeSubtitleContentResult {
    data class Ready(val content: ByteArray) : YouTubeSubtitleContentResult
    data object InvalidRequest : YouTubeSubtitleContentResult
    data object NotFound : YouTubeSubtitleContentResult
    data object Throttled : YouTubeSubtitleContentResult
    data object InvalidPayload : YouTubeSubtitleContentResult
    data object Unavailable : YouTubeSubtitleContentResult
}

internal fun isYouTubeTimedTextUrl(rawUrl: String): Boolean {
    val url = rawUrl.toHttpUrlOrNull() ?: return false
    return url.isHttps &&
        (url.host == "youtube.com" || url.host.endsWith(".youtube.com")) &&
        url.encodedPath == "/api/timedtext"
}

private fun ByteArray.isWebVtt(): Boolean =
    toString(Charsets.UTF_8).trimStart().startsWith("WEBVTT")

@Serializable
private data class TokenSubtitleError(val code: String? = null)
