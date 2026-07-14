package dev.typetype.server.services

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class TypetypeTokenSabrTokenClient(
    private val tokenServiceUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun fetch(videoId: String, forceRefresh: Boolean = false, refreshVideo: Boolean = false): SabrTokenBundle? {
        val url = buildUrl(videoId, forceRefresh, refreshVideo)
        return try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    System.err.println("[TypetypeTokenSabrTokenClient] /potoken HTTP ${resp.code} for $videoId")
                    return null
                }
                SabrTokenBundle.fromResponse(videoId, JSONObject(resp.body.string())).also {
                    if (it == null) {
                        System.err.println("[TypetypeTokenSabrTokenClient] incomplete token response for $videoId")
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[TypetypeTokenSabrTokenClient] fetch failed for $videoId: ${e.message}")
            null
        }
    }

    private fun buildUrl(videoId: String, forceRefresh: Boolean, refreshVideo: Boolean): String {
        val encodedVideoId = URLEncoder.encode(videoId, StandardCharsets.UTF_8)
        return "${tokenServiceUrl.trimEnd('/')}/potoken?videoId=$encodedVideoId" +
            (if (forceRefresh) "&refresh=true" else "") +
            (if (refreshVideo) "&refreshVideo=true" else "")
    }
}
