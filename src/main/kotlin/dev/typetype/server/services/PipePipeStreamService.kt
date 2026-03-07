package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoItem
import dev.typetype.server.models.VideoStreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

class PipePipeStreamService : StreamService {

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        withContext(Dispatchers.IO) {
            runCatching { StreamInfo.getInfo(url) }
                .fold(
                    onSuccess = { ExtractionResult.Success(it.toStreamResponse()) },
                    onFailure = { ExtractionResult.Failure(it.message ?: "Extraction failed") }
                )
        }

    private fun StreamInfo.toStreamResponse(): StreamResponse = StreamResponse(
        id = id,
        title = name ?: "",
        uploaderName = uploaderName ?: "",
        uploaderUrl = uploaderUrl ?: "",
        thumbnailUrl = thumbnailUrl ?: "",
        description = description?.content ?: "",
        duration = duration,
        viewCount = viewCount,
        likeCount = likeCount,
        dislikeCount = dislikeCount,
        uploadDate = textualUploadDate ?: "",
        hlsUrl = hlsUrl?.takeIf { it.startsWith("http") } ?: "",
        dashMpdUrl = dashMpdUrl?.takeIf { it.startsWith("http") } ?: "",
        videoStreams = videoStreams.map { it.toVideoStreamItem(isVideoOnly = false) },
        audioStreams = audioStreams.map { it.toAudioStreamItem() },
        videoOnlyStreams = videoOnlyStreams.map { it.toVideoStreamItem(isVideoOnly = true) },
        sponsorBlockSegments = getSponsorBlockSegments().map { it.toSegmentItem() },
        relatedStreams = relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
    )

    private fun VideoStream.toVideoStreamItem(isVideoOnly: Boolean): VideoStreamItem = VideoStreamItem(
        url = getContent() ?: "",
        format = getFormat()?.name ?: "",
        resolution = getResolution(),
        bitrate = getBitrate().takeIf { it > 0 },
        codec = getCodec() ?: "",
        isVideoOnly = isVideoOnly
    )

    private fun AudioStream.toAudioStreamItem(): AudioStreamItem = AudioStreamItem(
        url = getContent() ?: "",
        format = getFormat()?.name ?: "",
        bitrate = averageBitrate.takeIf { it > 0 },
        codec = getCodec() ?: "",
        quality = getQuality()
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
