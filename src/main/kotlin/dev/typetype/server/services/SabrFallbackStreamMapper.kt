package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoStreamItem
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun StreamResponse.withSabrFallback(videoId: String, info: YoutubeSabrInfo): StreamResponse {
    val video = info.formats.filter { it.isVideo }.mapNotNull { it.toFallbackVideo(videoId) }
    val audio = info.formats.filter { it.isAudio }.mapNotNull { it.toFallbackAudio(videoId) }
    return copy(
        videoOnlyStreams = video,
        audioStreams = audio,
        originalAudioTrackId = audio.firstOrNull { it.isOriginal }?.audioTrackId,
        preferredDefaultAudioTrackId = info.formats.firstOrNull { it.isAudio && it.isAudioDefault }?.audioTrackId,
    )
}

private fun YoutubeSabrFormat.toFallbackVideo(videoId: String): VideoStreamItem? {
    val type = mimeType?.takeIf { it.isNotBlank() } ?: return null
    val container = type.substringBefore(';').trim()
    return VideoStreamItem(
        url = "",
        mimeType = container,
        format = container.substringAfter('/').uppercase(),
        resolution = qualityLabel ?: height.takeIf { it > 0 }?.let { "${it}p" }.orEmpty(),
        bitrate = bitrate.takeIf { it > 0 },
        codec = type.codec(),
        isVideoOnly = true,
        itag = itag,
        width = width.coerceAtLeast(0),
        height = height.coerceAtLeast(0),
        fps = 0,
        contentLength = contentLength.coerceAtLeast(0L),
        initStart = initRangeStart.coerceAtLeast(0L),
        initEnd = initRangeEnd.coerceAtLeast(0L),
        indexStart = 0L,
        indexEnd = 0L,
        deliveryMethod = SABR_METHOD,
        manifestUrl = "/sabr/manifest/$videoId",
        sabrSessionUrl = "/sabr/session/$videoId?videoItag=$itag",
    )
}

private fun YoutubeSabrFormat.toFallbackAudio(videoId: String): AudioStreamItem? {
    val type = mimeType?.takeIf { it.isNotBlank() } ?: return null
    val container = type.substringBefore(';').trim()
    return AudioStreamItem(
        url = "",
        mimeType = container,
        format = container.substringAfter('/').uppercase(),
        bitrate = bitrate.takeIf { it > 0 },
        codec = type.codec(),
        quality = audioQuality,
        itag = itag,
        contentLength = contentLength.coerceAtLeast(0L),
        initStart = initRangeStart.coerceAtLeast(0L),
        initEnd = initRangeEnd.coerceAtLeast(0L),
        indexStart = 0L,
        indexEnd = 0L,
        audioTrackId = audioTrackId,
        audioTrackName = audioTrackDisplayName,
        audioLocale = audioTrackId?.substringBefore('.'),
        isOriginal = isOriginalAudio,
        deliveryMethod = SABR_METHOD,
        manifestUrl = "/sabr/manifest/$videoId",
        sabrSessionUrl = audioSessionUrl(videoId),
    )
}

private fun YoutubeSabrFormat.audioSessionUrl(videoId: String): String {
    val track = audioTrackId?.takeIf { it.isNotBlank() }
        ?.let { "&audioTrackId=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" }
        .orEmpty()
    return "/sabr/session/$videoId?audioItag=$itag$track"
}

private fun String.codec(): String? = substringAfter("codecs=", "")
    .substringBefore(';')
    .trim()
    .trim('"')
    .takeIf { it.isNotBlank() }

private const val SABR_METHOD = "sabr"
