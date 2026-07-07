package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder

internal fun SabrSessionHolder.toPlaybackResponse(
    videoId: String,
    startTimeMs: Long,
    ready: Boolean,
    retryAfterMs: Long?,
): SabrPlaybackResponse = SabrPlaybackResponse(
    sessionId = sessionToken,
    videoId = videoId,
    manifestUrl = if (ready) SabrPlaybackPaths.manifestPath(sessionToken) else null,
    videoItag = videoFormat.itag,
    audioItag = audioFormat.itag,
    audioTrackId = audioFormat.audioTrackId,
    startTimeMs = startTimeMs,
    ready = ready,
    status = if (ready) "ready" else playbackState().name.lowercase(),
    retryAfterMs = if (ready) null else retryAfterMs,
)

internal fun SabrSessionHolder.toRetryPlaybackResponse(status: String, retryAfterMs: Long): SabrPlaybackResponse =
    SabrPlaybackResponse(
        sessionId = sessionToken,
        videoId = key.videoId,
        manifestUrl = null,
        videoItag = videoFormat.itag,
        audioItag = audioFormat.itag,
        audioTrackId = audioFormat.audioTrackId,
        startTimeMs = playerTimeMs(),
        ready = false,
        status = status,
        retryAfterMs = retryAfterMs,
    )
