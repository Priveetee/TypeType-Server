package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

internal const val YOUTUBE_SESSION_REQUIRED_CODE = "youtube_session_required"
internal const val YOUTUBE_SESSION_REQUIRED_ERROR = "Connect YouTube to access this video"

internal class YoutubeSessionSabrStreamService(
    private val metadataService: YoutubeSessionStreamService,
    private val infoService: AuthenticatedSabrInfoService,
) {
    suspend fun getStreamInfo(userId: String, url: String): ExtractionResult<StreamResponse>? {
        val metadata = metadataService.getStreamInfo(userId, url) ?: return null
        if (metadata !is ExtractionResult.Success) return metadata
        val videoId = youtubeVideoId(url) ?: return ExtractionResult.BadRequest("Invalid YouTube URL")
        return when (val info = infoService.fetch(userId, videoId)) {
            is AuthenticatedSabrInfoResult.Ready ->
                ExtractionResult.Success(metadata.data.withSabrFallback(videoId, info.prepared.info))
            AuthenticatedSabrInfoResult.Failed ->
                ExtractionResult.Failure("Authenticated SABR playback unavailable")
            AuthenticatedSabrInfoResult.NotConnected -> null
        }
    }
}

internal fun ExtractionResult<StreamResponse>.requiresYoutubeSession(): Boolean =
    when (this) {
        is ExtractionResult.Success -> data.requiresMembership
        is ExtractionResult.BadRequest -> code == "age_restricted" || code == "members_only"
        is ExtractionResult.Failure -> false
    }
