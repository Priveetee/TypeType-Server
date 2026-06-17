package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

const val YOUTUBE_SESSION_RECONNECT_ERROR = "YouTube session needs reconnect"

class YoutubeSessionStreamService(
    private val streamService: StreamService,
    private val youtubeSessionService: YoutubeSessionService,
    private val cache: CacheService,
) {
    suspend fun getStreamInfo(userId: String, url: String): ExtractionResult<StreamResponse>? {
        if (!isYoutubeUrl(url)) return null
        val credentials = youtubeSessionService.connectedCredentials(userId) ?: return null
        val result = authenticatedCache(credentials, url)
        if (result is ExtractionResult.Success) {
            youtubeSessionService.markUsed(userId)
            return result
        }
        if (requiresReconnect(result)) {
            youtubeSessionService.markNeedsReconnect(userId)
            return ExtractionResult.BadRequest(YOUTUBE_SESSION_RECONNECT_ERROR)
        }
        youtubeSessionService.markUsed(userId)
        return result
    }

    private suspend fun authenticatedCache(
        credentials: YoutubeSessionCredentials,
        url: String,
    ): ExtractionResult<StreamResponse> = PublicExtractionCache.getOrLoad(
        cache = cache,
        area = "stream-auth",
        key = PublicCacheKey.of("stream-auth", credentials.userId, credentials.fingerprint, url),
        serializer = StreamResponse.serializer(),
        ttlSeconds = { minOf(it.streamCacheTtlSeconds(), AUTHENTICATED_STREAM_MAX_TTL_SECONDS) },
    ) {
        YoutubeSessionTokenScope.withCredentials(credentials) {
            streamService.getStreamInfo(url)
        }
    }

    private fun requiresReconnect(result: ExtractionResult<StreamResponse>): Boolean = when (result) {
        is ExtractionResult.Success -> false
        is ExtractionResult.BadRequest -> result.message.isSessionRejected()
        is ExtractionResult.Failure -> result.message.isSessionRejected()
    }

    private fun String.isSessionRejected(): Boolean {
        val message = lowercase()
        return rejectionSignals.any { it in message }
    }

    private companion object {
        const val AUTHENTICATED_STREAM_MAX_TTL_SECONDS = 900L
        val rejectionSignals = listOf(
            "sign in",
            "not a bot",
            "login",
            "cookie",
            "sapisid",
            "po token",
            "pot",
            "unauthorized",
            "forbidden",
            "no suitable stream",
            "failed to load stream",
        )
    }
}
