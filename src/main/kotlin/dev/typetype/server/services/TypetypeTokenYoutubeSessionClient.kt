package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class TypetypeTokenYoutubeSessionClient(
    private val tokenServiceUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetchHlsManifestUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val encodedVideoId = URLEncoder.encode(videoId, StandardCharsets.UTF_8)
        val url = "${tokenServiceUrl.trimEnd('/')}/youtube/sabr/session?videoId=$encodedVideoId&client=MWEB"
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body.string()).optString("hlsManifestUrl").trim().ifBlank { null }
            }
        }.getOrNull()
    }
}
