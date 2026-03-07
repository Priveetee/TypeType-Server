package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoStreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
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
        hlsUrl = hlsUrl ?: "",
        dashMpdUrl = dashMpdUrl ?: "",
        videoStreams = videoStreams.map { it.toVideoStreamItem(isVideoOnly = false) },
        audioStreams = audioStreams.map { it.toAudioStreamItem() },
        videoOnlyStreams = videoOnlyStreams.map { it.toVideoStreamItem(isVideoOnly = true) },
        sponsorBlockSegments = getSponsorBlockSegments().map { it.toSegmentItem() }
    )

    private fun VideoStream.toVideoStreamItem(isVideoOnly: Boolean): VideoStreamItem = VideoStreamItem(
        url = getContent() ?: "",
        format = getFormat()?.name ?: "",
        resolution = getResolution(),
        bitrate = getBitrate(),
        codec = getCodec() ?: "",
        isVideoOnly = isVideoOnly
    )

    private fun AudioStream.toAudioStreamItem(): AudioStreamItem = AudioStreamItem(
        url = getContent() ?: "",
        format = getFormat()?.name ?: "",
        bitrate = averageBitrate,
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
}
