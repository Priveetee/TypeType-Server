package dev.typetype.server.services

import com.grack.nanojson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class TypetypeTokenYoutubeSessionClient(
    private val tokenServiceUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetchHlsManifestUrl(videoId: String): String? = fetchSession(videoId)
        ?.optString("hlsManifestUrl")
        ?.trim()
        ?.ifBlank { null }

    suspend fun fetchSabrInfo(videoId: String): YoutubeSabrInfo? = fetchSession(videoId)?.let { session ->
        runCatching {
            val playerResponse = JSONObject()
                .put("responseContext", JSONObject().put("visitorData", session.getString("visitorData")))
                .put(
                    "streamingData",
                    JSONObject()
                        .put("serverAbrStreamingUrl", session.getString("serverAbrStreamingUrl"))
                        .put("adaptiveFormats", session.getJSONArray("adaptiveFormats")),
                )
                .put(
                    "playerConfig",
                    JSONObject().put(
                        "mediaCommonConfig",
                        JSONObject().put(
                            "mediaUstreamerRequestConfig",
                            JSONObject().put(
                                "videoPlaybackUstreamerConfig",
                                session.getString("videoPlaybackUstreamerConfig"),
                            ),
                        ),
                    ),
                )
            YoutubeSabrProbe.fromPlayerResponse(
                videoId,
                YoutubeSabrClientProfile.MWEB,
                YoutubeParsingHelper.generateContentPlaybackNonce(),
                JsonParser.`object`().from(playerResponse.toString()),
            )
        }.getOrNull()
    }

    private suspend fun fetchSession(videoId: String): JSONObject? = withContext(Dispatchers.IO) {
        val encodedVideoId = URLEncoder.encode(videoId, StandardCharsets.UTF_8)
        val url = "${tokenServiceUrl.trimEnd('/')}/youtube/sabr/session?videoId=$encodedVideoId&client=MWEB"
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body.string())
            }
        }.getOrNull()
    }
}
