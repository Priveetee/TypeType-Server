package dev.typetype.server.services

import dev.typetype.server.models.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

internal class YouTubeSubtitleService(private val httpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchSubtitles(videoId: String): List<SubtitleItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("http://localhost:8081/subtitles?videoId=$videoId")
                        .build()
                ).execute()
                json.decodeFromString<List<SubtitleItem>>(
                    response.body?.string() ?: return@runCatching emptyList()
                )
            }.getOrElse { emptyList() }
        }
}
