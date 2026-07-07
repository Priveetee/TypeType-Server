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
    val playerTimeMs: Long,
    val readerHeadMs: Long,
    val readerTailMs: Long,
    val bufferedEdgeMs: Long,
    val cachedBytes: Long,
    val terminalError: String? = null,
)
