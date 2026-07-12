package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

internal class SabrFallbackStreamService(
    private val delegate: StreamService,
    private val sessionStore: SabrSessionStore,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        val result = delegate.getStreamInfo(url)
        if (!isYoutubeUrl(url)) return result
        val response = (result as? ExtractionResult.Success)?.data ?: return result
        if (response.hasPlayableStreams()) return result
        val videoId = youtubeVideoId(url) ?: return result
        val prepared = sessionStore.fetchInfo(videoId, cachedFirst = true) ?: return result
        return ExtractionResult.Success(response.withSabrFallback(videoId, prepared.info))
    }
}

private fun StreamResponse.hasPlayableStreams(): Boolean =
    videoStreams.isNotEmpty() || videoOnlyStreams.isNotEmpty() || audioStreams.isNotEmpty() ||
        hlsUrl.isNotBlank() || dashMpdUrl.isNotBlank()

private fun youtubeVideoId(url: String): String? =
    Regex("(?:[?&]v=|/shorts/|youtu\\.be/)([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
