package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class SabrFallbackStreamService(
    private val delegate: StreamService,
    private val sessionStore: SabrSessionStore,
    private val tokenSessionClient: TypetypeTokenYoutubeSessionClient,
    private val liveFallback: StreamService? = null,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> = coroutineScope {
        if (!isYoutubeUrl(url)) return@coroutineScope delegate.getStreamInfo(url)
        val videoId = youtubeVideoId(url)
        val prepared = videoId?.let { async { sessionStore.fetchInfo(it, cachedFirst = true) } }
        val result = delegate.getStreamInfo(url)
        val response = (result as? ExtractionResult.Success)?.data
        val live = result.resolveLiveFallback(url, liveFallback)
        if (live != null) return@coroutineScope live
        if (response == null) {
            if (result !is ExtractionResult.Failure || videoId == null) return@coroutineScope result
            prepared?.await()
            val session = tokenSessionClient.fetchPlaybackSession(videoId) ?: return@coroutineScope result
            return@coroutineScope ExtractionResult.Success(session.toFallbackStreamResponse(videoId))
        }
        val playable = prepared?.await()
        if (response.hasPlayableStreams() || videoId == null || playable == null) return@coroutineScope result
        ExtractionResult.Success(response.withSabrFallback(videoId, playable.info))
    }
}

private suspend fun ExtractionResult<StreamResponse>.resolveLiveFallback(
    url: String,
    liveFallback: StreamService?,
): ExtractionResult.Success<StreamResponse>? {
    val response = (this as? ExtractionResult.Success)?.data
    if (response != null && (!response.isLive || response.hlsUrl.isNotBlank())) return null
    val fallback = liveFallback?.getStreamInfo(url) as? ExtractionResult.Success ?: return null
    return fallback.takeIf { it.data.isLive && it.data.hlsUrl.isNotBlank() }
}

private fun StreamResponse.hasPlayableStreams(): Boolean =
    videoStreams.isNotEmpty() || videoOnlyStreams.isNotEmpty() || audioStreams.isNotEmpty() ||
        hlsUrl.isNotBlank() || dashMpdUrl.isNotBlank()
