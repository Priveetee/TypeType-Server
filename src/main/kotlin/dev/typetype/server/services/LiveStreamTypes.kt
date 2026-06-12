package dev.typetype.server.services

import org.schabi.newpipe.extractor.stream.StreamType

internal data class StreamLiveMetadata(
    val streamType: String,
    val hlsUrl: String,
    val dashMpdUrl: String,
    val isLive: Boolean,
    val isPostLive: Boolean,
    val isLiveContent: Boolean,
    val hasLiveManifest: Boolean,
)

internal fun StreamType?.toApiStreamType(fallback: String = ""): String = this?.name?.lowercase() ?: fallback

internal fun streamLiveMetadata(streamType: StreamType?, hlsUrl: String?, dashMpdUrl: String?): StreamLiveMetadata {
    val apiStreamType = streamType.toApiStreamType()
    val apiHlsUrl = hlsUrl?.takeIf { it.startsWith("http") } ?: ""
    val apiDashMpdUrl = dashMpdUrl?.takeIf { it.startsWith("http") } ?: ""
    return StreamLiveMetadata(
        streamType = apiStreamType,
        hlsUrl = apiHlsUrl,
        dashMpdUrl = apiDashMpdUrl,
        isLive = apiStreamType.isLiveStreamType(),
        isPostLive = apiStreamType.isPostLiveStreamType(),
        isLiveContent = apiStreamType.isLiveContentType(),
        hasLiveManifest = apiStreamType.isLiveStreamType() && (apiHlsUrl.isNotBlank() || apiDashMpdUrl.isNotBlank()),
    )
}

internal fun String.isLiveStreamType(): Boolean = this == "live_stream" || this == "audio_live_stream"

internal fun String.isPostLiveStreamType(): Boolean = this == "post_live_stream" || this == "post_live_audio_stream"

internal fun String.isLiveContentType(): Boolean = isLiveStreamType() || isPostLiveStreamType()
