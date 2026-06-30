package dev.typetype.server.services
import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.SubtitleItem
import dev.typetype.server.models.VideoStreamItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

internal fun StreamInfo.toStreamResponse(): StreamResponse {
    val uploaded = uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: -1L
    val liveMetadata = streamLiveMetadata(streamType, hlsUrl, dashMpdUrl)
    return StreamResponse(
        id = id,
        title = name ?: "",
        uploaderName = uploaderName ?: "",
        uploaderUrl = uploaderUrl ?: "",
        uploaderAvatarUrl = uploaderAvatarUrl ?: "",
        thumbnailUrl = thumbnailUrl.toAbsoluteUrl(),
        description = description?.content ?: "",
        duration = duration,
        viewCount = viewCount,
        likeCount = likeCount,
        dislikeCount = dislikeCount,
        uploadDate = textualUploadDate ?: "",
        uploaded = uploaded,
        publishedAt = PublishedAtMapper.fromUploaded(uploaded),
        uploaderSubscriberCount = uploaderSubscriberCount,
        uploaderVerified = isUploaderVerified,
        category = category ?: "",
        license = licence ?: "",
        visibility = privacy?.name?.lowercase() ?: "",
        tags = tags ?: emptyList(),
        streamType = liveMetadata.streamType,
        isLive = liveMetadata.isLive,
        isPostLive = liveMetadata.isPostLive,
        isLiveContent = liveMetadata.isLiveContent,
        hasLiveManifest = liveMetadata.hasLiveManifest,
        isShortFormContent = isShortFormContent,
        requiresMembership = requiresMembership(),
        startPosition = startPosition,
        streamSegments = runCatching { streamSegments.map { it.toStreamSegmentItem() } }.getOrElse { emptyList() },
        hlsUrl = liveMetadata.hlsUrl,
        dashMpdUrl = liveMetadata.dashMpdUrl,
        videoStreams = videoStreams.map { it.toVideoStreamItem(id, false) },
        audioStreams = audioStreams.mapNotNull { runCatching { it.toAudioStreamItem(id) }.getOrNull() },
        originalAudioTrackId = null,
        preferredDefaultAudioTrackId = null,
        videoOnlyStreams = videoOnlyStreams.map { it.toVideoStreamItem(id, true) },
        subtitles = subtitles.mapNotNull { runCatching { it.toSubtitleItem() }.getOrNull() },
        previewFrames = previewFrames.mapNotNull { runCatching { it.toPreviewFrameItem() }.getOrNull() },
        sponsorBlockSegments = runCatching { getSponsorBlockSegments().map { it.toSegmentItem() } }.getOrElse { emptyList() },
        relatedStreams = relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { runCatching { it.toVideoItem() }.getOrNull() },
    )
}
internal fun VideoStream.toVideoStreamItem(videoId: String, isVideoOnly: Boolean): VideoStreamItem {
    val method = deliveryMethodName()
    return VideoStreamItem(
        url = playableUrl(method),
        mimeType = getFormat()?.getMimeType() ?: "",
        format = getFormat()?.name ?: "",
        resolution = getResolution(),
        bitrate = getBitrate().takeIf { it > 0 },
        codec = getCodec()?.takeIf { it.isNotBlank() },
        isVideoOnly = isVideoOnly,
        itag = getItag(),
        width = getWidth(),
        height = getHeight(),
        fps = getFps(),
        contentLength = getItagItem()?.getContentLength() ?: 0L,
        initStart = getInitStart().toLong(),
        initEnd = getInitEnd().toLong(),
        indexStart = getIndexStart().toLong(),
        indexEnd = getIndexEnd().toLong(),
        deliveryMethod = method,
        manifestUrl = if (method == "sabr") "/sabr/manifest/$videoId" else null,
    )
}

internal fun AudioStream.toAudioStreamItem(videoId: String): AudioStreamItem {
    val method = deliveryMethodName()
    return AudioStreamItem(
        url = playableUrl(method),
        mimeType = getFormat()?.getMimeType() ?: "",
        format = getFormat()?.name ?: "",
        bitrate = averageBitrate.takeIf { it > 0 },
        codec = getCodec()?.takeIf { it.isNotBlank() },
        quality = getQuality(),
        itag = getItag(),
        contentLength = getItagItem()?.getContentLength() ?: 0L,
        initStart = getInitStart().toLong(),
        initEnd = getInitEnd().toLong(),
        indexStart = getIndexStart().toLong(),
        indexEnd = getIndexEnd().toLong(),
        audioTrackId = getAudioTrackId(),
        audioTrackName = getAudioTrackName(),
        audioLocale = getAudioLocale(),
        isOriginal = false,
        deliveryMethod = method,
        manifestUrl = if (method == "sabr") "/sabr/manifest/$videoId" else null,
    )
}

private fun VideoStream.deliveryMethodName(): String = when (getDeliveryMethod().name) {
    "PROGRESSIVE_HTTP" -> "progressive"
    "DASH" -> "dash"
    "HLS" -> "hls"
    "SABR" -> "sabr"
    else -> "progressive"
}

private fun AudioStream.deliveryMethodName(): String = when (getDeliveryMethod().name) {
    "PROGRESSIVE_HTTP" -> "progressive"
    "DASH" -> "dash"
    "HLS" -> "hls"
    "SABR" -> "sabr"
    else -> "progressive"
}

private fun VideoStream.playableUrl(method: String): String =
    if (method == "sabr" || !isUrl) "" else getContent().orEmpty()

private fun AudioStream.playableUrl(method: String): String =
    if (method == "sabr" || !isUrl) "" else getContent().orEmpty()

internal fun SubtitlesStream.toSubtitleItem(): SubtitleItem = SubtitleItem(
    url = getContent() ?: "",
    mimeType = getFormat()?.getMimeType() ?: "",
    languageTag = getLanguageTag() ?: "",
    displayLanguageName = getDisplayLanguageName() ?: "",
    isAutoGenerated = isAutoGenerated,
)
