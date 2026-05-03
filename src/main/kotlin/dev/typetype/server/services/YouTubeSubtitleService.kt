package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class YouTubeSubtitleService(private val httpClient: OkHttpClient, private val baseUrl: String) {

    suspend fun fetchSubtitles(videoId: String): List<SubtitleItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("$baseUrl/subtitles?videoId=$videoId")
                        .build()
                ).execute()
                response.use { CacheJson.decodeFromString<List<SubtitleItem>>(it.body.string()) }
            }.getOrElse { emptyList() }
        }
}
