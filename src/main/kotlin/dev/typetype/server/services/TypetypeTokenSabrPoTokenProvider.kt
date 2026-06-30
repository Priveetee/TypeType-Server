package dev.typetype.server.services

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrPoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.util.Base64

class TypetypeTokenSabrPoTokenProvider(
    private val tokenServiceUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) : SabrPoTokenProvider {

    override fun getPoToken(info: YoutubeSabrInfo, streamState: YoutubeSabrStreamState): ByteArray? =
        fetch(info.videoId, forceRefresh = false)

    override fun getPoToken(
        info: YoutubeSabrInfo,
        streamState: YoutubeSabrStreamState,
        forceRefresh: Boolean,
    ): ByteArray? = fetch(info.videoId, forceRefresh)

    private fun fetch(videoId: String, forceRefresh: Boolean): ByteArray? {
        val url = "${tokenServiceUrl.trimEnd('/')}/potoken?videoId=$videoId" +
            if (forceRefresh) "&refresh=true" else ""
        return try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    System.err.println("[TypetypeTokenSabrPoTokenProvider] /potoken HTTP ${resp.code} for $videoId")
                    return null
                }
                val json = JSONObject(resp.body.string())
                val streamingPot = json.optString("streamingPot")
                if (streamingPot.isBlank()) {
                    System.err.println("[TypetypeTokenSabrPoTokenProvider] no streamingPot in response for $videoId")
                    return null
                }
                base64UrlDecode(streamingPot)
            }
        } catch (e: Exception) {
            System.err.println("[TypetypeTokenSabrPoTokenProvider] fetch failed for $videoId: ${e.message}")
            null
        }
    }

    private fun base64UrlDecode(s: String): ByteArray {
        val padded = s + "=".repeat((4 - s.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded)
    }
}