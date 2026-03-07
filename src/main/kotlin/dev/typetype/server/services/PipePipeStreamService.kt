package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoItem
import dev.typetype.server.models.VideoStreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

class PipePipeStreamService : StreamService {

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(30_000L) { StreamInfo.getInfo(url) }.toStreamResponse()
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Extraction failed") }
            )
        }

    private fun StreamInfo.toStreamResponse(): StreamResponse = StreamResponse(
        id = id,
        title = name ?: "",
        uploaderName = uploaderName ?: "",
        uploaderUrl = uploaderUrl ?: "",
        uploaderAvatarUrl = uploaderAvatarUrl ?: "",
        thumbnailUrl = thumbnailUrl ?: "",
        description = description?.content ?: "",
        duration = duration,
        viewCount = viewCount,
        likeCount = likeCount,
        dislikeCount = dislikeCount,
        uploadDate = textualUploadDate ?: "",
        hlsUrl = hlsUrl?.takeIf { it.startsWith("http") } ?: "",
        dashMpdUrl = dashMpdUrl?.takeIf { it.startsWith("http") } ?: "",
        videoStreams = videoStreams.mapNotNull { runCatching { it.toVideoStreamItem(false) }.getOrNull() },
        audioStreams = audioStreams.mapNotNull { runCatching { it.toAudioStreamItem() }.getOrNull() },
        videoOnlyStreams = videoOnlyStreams.mapNotNull { runCatching { it.toVideoStreamItem(true) }.getOrNull() },
        sponsorBlockSegments = runCatching { getSponsorBlockSegments().map { it.toSegmentItem() } }.getOrElse { emptyList() },
        relatedStreams = relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { runCatching { it.toVideoItem() }.getOrNull() },
    )

    private fun VideoStream.toVideoStreamItem(isVideoOnly: Boolean): VideoStreamItem = VideoStreamItem(
        url = getContent() ?: "",
        format = getFormat()?.name ?: "",
        resolution = getResolution(),
        bitrate = getBitrate().takeIf { it > 0 },
        codec = getCodec() ?: "",
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
    )

    private fun AudioStream.toAudioStreamItem(): AudioStreamItem = AudioStreamItem(
        url = getContent() ?: "",
        format = getFormat()?.name ?: "",
        bitrate = averageBitrate.takeIf { it > 0 },
        codec = getCodec() ?: "",
        quality = getQuality(),
        itag = getItag(),
        contentLength = getItagItem()?.getContentLength() ?: 0L,
        initStart = getInitStart().toLong(),
        initEnd = getInitEnd().toLong(),
        indexStart = getIndexStart().toLong(),
        indexEnd = getIndexEnd().toLong(),
    )

    private fun org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment.toSegmentItem() =
        SponsorBlockSegmentItem(
            startTime = startTime,
            endTime = endTime,
            category = category.apiName,
            action = action.apiName
        )

    private fun StreamInfoItem.toVideoItem(): VideoItem = VideoItem(
        id = url ?: "",
        title = name ?: "",
        url = url ?: "",
        thumbnailUrl = thumbnailUrl ?: "",
        uploaderName = uploaderName ?: "",
        uploaderUrl = uploaderUrl ?: "",
        uploaderAvatarUrl = uploaderAvatarUrl ?: "",
        duration = duration,
        viewCount = viewCount,
        uploadDate = textualUploadDate ?: "",
    )
}
