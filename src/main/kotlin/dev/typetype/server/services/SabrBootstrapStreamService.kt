package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class SabrBootstrapStreamService(
    private val sessionStore: SabrSessionStore,
    private val tokenSessionClient: TypetypeTokenYoutubeSessionClient,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> = coroutineScope {
        val videoId = youtubeVideoId(url)
            ?: return@coroutineScope ExtractionResult.BadRequest("Invalid YouTube URL")
        val prepared = async { sessionStore.fetchInfo(videoId, cachedFirst = true) }
        val session = async { tokenSessionClient.fetchPlaybackSession(videoId) }
        val metadata = session.await()
            ?: return@coroutineScope ExtractionResult.Failure("SABR bootstrap metadata unavailable")
        if (prepared.await() == null) {
            return@coroutineScope ExtractionResult.Failure("SABR playback formats unavailable")
        }
        ExtractionResult.Success(metadata.toFallbackStreamResponse(videoId))
    }
}

internal fun youtubeVideoId(url: String): String? =
    Regex("(?:[?&]v=|/shorts/|youtu\\.be/)([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
