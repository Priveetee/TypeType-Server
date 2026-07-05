package dev.typetype.server.routes

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoStreamItem
import dev.typetype.server.services.SabrSessionStore
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

internal suspend fun StreamResponse.withPlayableSabrStreams(
    url: String,
    sabrSessionStore: SabrSessionStore,
): StreamResponse {
    if (!hasSabrStreams()) return this
    val videoId = url.youtubeVideoId() ?: return this
    val prepared = sabrSessionStore.fetchInfo(videoId, cachedFirst = true)
        ?: return withoutSabrStreams()
    val hasAudio = SabrFormatSelector.audio(prepared.info, null, null, requireAac = true) != null
    if (!hasAudio) return withoutSabrStreams()
    return copy(
        videoStreams = videoStreams.filter { it.isPlayableSabrVideo(prepared.info) },
        videoOnlyStreams = videoOnlyStreams.filter { it.isPlayableSabrVideo(prepared.info) },
        audioStreams = audioStreams.filter { it.isPlayableSabrAudio(prepared.info) },
    )
}

private fun StreamResponse.hasSabrStreams(): Boolean =
    videoStreams.any { it.deliveryMethod == SABR_DELIVERY_METHOD } ||
        videoOnlyStreams.any { it.deliveryMethod == SABR_DELIVERY_METHOD } ||
        audioStreams.any { it.deliveryMethod == SABR_DELIVERY_METHOD }

private fun StreamResponse.withoutSabrStreams(): StreamResponse = copy(
    videoStreams = videoStreams.filterNot { it.deliveryMethod == SABR_DELIVERY_METHOD },
    videoOnlyStreams = videoOnlyStreams.filterNot { it.deliveryMethod == SABR_DELIVERY_METHOD },
    audioStreams = audioStreams.filterNot { it.deliveryMethod == SABR_DELIVERY_METHOD },
)

private fun VideoStreamItem.isPlayableSabrVideo(info: YoutubeSabrInfo): Boolean =
    deliveryMethod != SABR_DELIVERY_METHOD || SabrFormatSelector.video(info, itag) != null

private fun AudioStreamItem.isPlayableSabrAudio(info: YoutubeSabrInfo): Boolean =
    deliveryMethod != SABR_DELIVERY_METHOD ||
    SabrFormatSelector.audio(info, itag, audioTrackId, requireAac = true) != null

private const val SABR_DELIVERY_METHOD = "sabr"
