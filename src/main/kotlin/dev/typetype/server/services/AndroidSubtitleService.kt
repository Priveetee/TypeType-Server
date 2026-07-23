package dev.typetype.server.services

internal class AndroidSubtitleService(
    private val inventorySource: YouTubeSubtitleService,
    private val httpClient: AndroidSubtitleHttpClient,
) {
    suspend fun inventory(videoId: String): AndroidSubtitleInventoryResult =
        when (val result = inventorySource.fetchSubtitleInventory(videoId)) {
            is YouTubeSubtitleInventoryResult.Ready -> {
                val tracks = AndroidSubtitleTrackFactory.create(videoId, result.tracks)
                    ?: return AndroidSubtitleInventoryResult.Unavailable
                AndroidSubtitleInventoryResult.Ready(tracks)
            }
            YouTubeSubtitleInventoryResult.Unavailable -> AndroidSubtitleInventoryResult.Unavailable
        }

    suspend fun content(
        videoId: String,
        track: AndroidSubtitleTrack,
    ): AndroidSubtitleContentResult {
        return when (val initial = httpClient.fetch(track.sourceUrl)) {
            is AndroidSubtitleUpstreamResult.Ready -> AndroidSubtitleContentResult.Ready(initial.bytes)
            AndroidSubtitleUpstreamResult.Unavailable -> AndroidSubtitleContentResult.Unavailable
            AndroidSubtitleUpstreamResult.TemporaryFailure -> retryWithFreshInventory(videoId, track.id)
        }
    }

    private suspend fun retryWithFreshInventory(
        videoId: String,
        trackId: String,
    ): AndroidSubtitleContentResult {
        val refreshed = inventory(videoId) as? AndroidSubtitleInventoryResult.Ready
            ?: return AndroidSubtitleContentResult.TemporaryFailure
        val track = refreshed.tracks.firstOrNull { it.id == trackId }
            ?: return AndroidSubtitleContentResult.Unavailable
        return when (val retry = httpClient.fetch(track.sourceUrl)) {
            is AndroidSubtitleUpstreamResult.Ready -> AndroidSubtitleContentResult.Ready(retry.bytes)
            AndroidSubtitleUpstreamResult.Unavailable -> AndroidSubtitleContentResult.Unavailable
            AndroidSubtitleUpstreamResult.TemporaryFailure -> AndroidSubtitleContentResult.TemporaryFailure
        }
    }
}

internal sealed interface AndroidSubtitleContentResult {
    data class Ready(val bytes: ByteArray) : AndroidSubtitleContentResult
    data object TemporaryFailure : AndroidSubtitleContentResult
    data object Unavailable : AndroidSubtitleContentResult
}
