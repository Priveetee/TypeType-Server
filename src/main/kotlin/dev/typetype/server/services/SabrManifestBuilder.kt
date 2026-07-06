package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

internal object SabrManifestBuilder {
    fun build(
        videoId: String,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        endSegmentAudio: Long,
        endSegmentVideo: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
        startSegmentAudio: Int = 1,
        startSegmentVideo: Int = 1,
    ): String = SabrDashManifestBuilder.build(
        videoId,
        audio,
        video,
        endSegmentAudio,
        endSegmentVideo,
        streamState,
        sessionToken,
        startSegmentAudio,
        startSegmentVideo,
    )

    fun buildAudioOnly(
        videoId: String,
        audio: YoutubeSabrFormat,
        endSegmentAudio: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
        startSegmentAudio: Int = 1,
    ): String = SabrDashManifestBuilder.buildAudioOnly(
        videoId,
        audio,
        endSegmentAudio,
        streamState,
        sessionToken,
        startSegmentAudio,
    )

    fun buildAudioOnlyHls(
        videoId: String,
        audio: YoutubeSabrFormat,
        endSegmentAudio: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ): String = SabrHlsManifestBuilder.buildAudioOnly(videoId, audio, endSegmentAudio, streamState, sessionToken)

    fun buildHlsMaster(
        videoId: String,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        sessionToken: String,
    ): String = SabrHlsManifestBuilder.buildMaster(videoId, audio, video, sessionToken)

    fun buildVideoOnlyHls(
        videoId: String,
        video: YoutubeSabrFormat,
        endSegmentVideo: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ): String = SabrHlsManifestBuilder.buildVideoOnly(videoId, video, endSegmentVideo, streamState, sessionToken)
}
