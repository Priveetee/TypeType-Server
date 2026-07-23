package dev.typetype.server.services

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

internal class AndroidSubtitleHttpClient(
    private val primaryClient: OkHttpClient,
    private val directClient: OkHttpClient?,
) {
    suspend fun fetch(sourceUrl: HttpUrl): AndroidSubtitleUpstreamResult {
        val url = sourceUrl.newBuilder()
            .removeAllQueryParameters("fmt")
            .addQueryParameter("fmt", "vtt")
            .build()
        val primary = request(primaryClient, url)
        if (primary !is AndroidSubtitleUpstreamResult.TemporaryFailure) return primary
        return directClient?.let { request(it, url) } ?: primary
    }

    private suspend fun request(client: OkHttpClient, url: HttpUrl): AndroidSubtitleUpstreamResult =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/vtt")
                .header("Origin", YOUTUBE_ORIGIN)
                .header("Referer", "$YOUTUBE_ORIGIN/")
                .header("User-Agent", ANDROID_VR_USER_AGENT)
                .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(AndroidSubtitleUpstreamResult.TemporaryFailure)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use(::readResponse) }
                        .getOrDefault(AndroidSubtitleUpstreamResult.TemporaryFailure)
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }

    private fun readResponse(response: Response): AndroidSubtitleUpstreamResult {
        if (response.code == 403 || response.code == 429 || response.code >= 500) {
            return AndroidSubtitleUpstreamResult.TemporaryFailure
        }
        if (!response.isSuccessful) return AndroidSubtitleUpstreamResult.Unavailable
        val body = response.body
        if (body.contentLength() > MAX_SUBTITLE_BYTES) return AndroidSubtitleUpstreamResult.Unavailable
        val bytes = body.byteStream().readNBytes(MAX_SUBTITLE_BYTES + 1)
        if (bytes.size > MAX_SUBTITLE_BYTES) return AndroidSubtitleUpstreamResult.Unavailable
        val text = runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull() ?: return AndroidSubtitleUpstreamResult.Unavailable
        if (!text.removePrefix("\uFEFF").startsWith("WEBVTT")) {
            return AndroidSubtitleUpstreamResult.Unavailable
        }
        return AndroidSubtitleUpstreamResult.Ready(bytes)
    }

    private companion object {
        const val MAX_SUBTITLE_BYTES = 16 * 1024 * 1024
        const val YOUTUBE_ORIGIN = "https://www.youtube.com"
        const val ANDROID_VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
                "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    }
}

internal sealed interface AndroidSubtitleUpstreamResult {
    data class Ready(val bytes: ByteArray) : AndroidSubtitleUpstreamResult
    data object TemporaryFailure : AndroidSubtitleUpstreamResult
    data object Unavailable : AndroidSubtitleUpstreamResult
}
