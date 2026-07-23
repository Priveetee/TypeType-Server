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
        return fetch(videoId, forceRefresh, refreshVideo, logIdentifier = true)
    }

    // The Token minter accepts an opaque binding even though its internal query parameter is named videoId.
    fun fetchBoundToken(binding: String): String? =
        fetch(binding, forceRefresh = false, refreshVideo = false, logIdentifier = false)?.videoBoundPoToken

    private fun fetch(
        binding: String,
        forceRefresh: Boolean,
        refreshVideo: Boolean,
        logIdentifier: Boolean,
    ): SabrTokenBundle? {
        val url = buildUrl(binding, forceRefresh, refreshVideo)
        val target = if (logIdentifier) " for $binding" else " for session binding"
        return try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    System.err.println("[TypetypeTokenSabrTokenClient] /potoken HTTP ${resp.code}$target")
                    return null
                }
                SabrTokenBundle.fromResponse(binding, JSONObject(resp.body.string())).also {
                    if (it == null) {
                        System.err.println("[TypetypeTokenSabrTokenClient] incomplete token response$target")
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[TypetypeTokenSabrTokenClient] fetch failed$target: ${e.message}")
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
