package dev.typetype.server.routes

import kotlinx.serialization.Serializable

@Serializable
internal data class SabrPlaybackRequest(
    val videoItag: Int? = null,
    val audioItag: Int? = null,
    val audioTrackId: String? = null,
    val startTimeMs: Long? = null,
    val playerTimeMs: Long? = null,
)

@Serializable
internal data class SabrPlaybackResponse(
    val sessionId: String,
    val videoId: String,
    val manifestUrl: String? = null,
    val videoItag: Int,
    val audioItag: Int,
    val audioTrackId: String? = null,
    val startTimeMs: Long,
    val generation: Long,
    val ready: Boolean,
    val status: String,
    val retryAfterMs: Long? = null,
)

@Serializable
internal data class SabrPlaybackStateResponse(
    val sessionId: String,
    val videoId: String,
    val state: String,
    val videoItag: Int,
    val audioItag: Int,
    val audioTrackId: String? = null,
    val generation: Long,
    val requestedSeekTimeMs: Long? = null,
    val playerTimeMs: Long,
    val readerHeadMs: Long,
    val readerTailMs: Long,
    val bufferedEdgeMs: Long,
    val cachedBytes: Long,
    val pendingRefetch: String? = null,
    val pendingForwardSeek: String? = null,
    val terminalError: String? = null,
)

@Serializable
internal data class SabrPlaybackWindowRequest(
    val generation: Long,
    val playerTimeMs: Long,
    val videoItag: Int,
    val audioItag: Int,
    val audioTrackId: String? = null,
    val bufferGoalMs: Long = 30_000L,
    val backBufferMs: Long = 30_000L,
)

@Serializable
internal data class SabrPlaybackWindowReadyResponse(
    val sessionId: String,
    val generation: Long,
    val ready: Boolean,
    val retryAfterMs: Long?,
    val durationMs: Long,
    val audio: SabrPlaybackWindowTrack,
    val video: SabrPlaybackWindowTrack,
)

@Serializable
internal data class SabrPlaybackWindowTrack(
    val mime: String,
    val initUrl: String,
    val segments: List<SabrPlaybackWindowSegment>,
)

@Serializable
internal data class SabrPlaybackWindowSegment(
    val url: String,
    val startMs: Long,
    val durationMs: Long,
)

@Serializable
internal data class SabrPlaybackWindowPreparingResponse(
    val sessionId: String,
    val generation: Long,
    val ready: Boolean,
    val retryAfterMs: Long,
    val status: String,
    val blockedBy: String,
    val playerTimeMs: Long,
    val readerHeadMs: Long,
    val readerTailMs: Long,
    val bufferedEdgeMs: Long,
    val pendingRefetch: String? = null,
    val pendingForwardSeek: String? = null,
    val terminalError: String? = null,
)
