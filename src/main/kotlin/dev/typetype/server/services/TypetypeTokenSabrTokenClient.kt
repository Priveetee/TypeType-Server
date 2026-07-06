package dev.typetype.server.services

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

internal class TypetypeTokenSabrTokenClient(
    private val tokenServiceUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun fetch(videoId: String, forceRefresh: Boolean = false): SabrTokenBundle? {
        val url = buildUrl(videoId, forceRefresh)
        return try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    System.err.println("[TypetypeTokenSabrTokenClient] /potoken HTTP ${resp.code} for $videoId")
                    return null
                }
                decode(videoId, JSONObject(resp.body.string()))
            }
        } catch (e: Exception) {
            System.err.println("[TypetypeTokenSabrTokenClient] fetch failed for $videoId: ${e.message}")
            null
        }
    }

    private fun decode(videoId: String, json: JSONObject): SabrTokenBundle? {
        val visitorBoundPoToken = json.optString("visitorBoundPoToken").ifBlank { json.optString("poToken") }
        val visitorData = json.optString("visitorData")
        val videoBoundPoToken = json.optString("videoBoundPoToken").ifBlank { json.optString("streamingPot") }
        if (visitorBoundPoToken.isBlank() || visitorData.isBlank() || videoBoundPoToken.isBlank()) {
            System.err.println("[TypetypeTokenSabrTokenClient] incomplete token response for $videoId")
            return null
        }
        return SabrTokenBundle(
            videoId = videoId,
            visitorBoundPoToken = visitorBoundPoToken,
            visitorBoundPoTokenBytes = base64UrlDecode(visitorBoundPoToken),
            visitorData = visitorData,
            videoBoundPoToken = videoBoundPoToken,
            videoBoundPoTokenBytes = base64UrlDecode(videoBoundPoToken),
        )
    }

    private fun buildUrl(videoId: String, forceRefresh: Boolean): String {
        val encodedVideoId = URLEncoder.encode(videoId, StandardCharsets.UTF_8)
        return "${tokenServiceUrl.trimEnd('/')}/potoken?videoId=$encodedVideoId" +
            if (forceRefresh) "&refreshVideo=true" else ""
    }

    private fun base64UrlDecode(s: String): ByteArray {
        val padded = s + "=".repeat((4 - s.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded)
    }
}
