package dev.typetype.server.routes

import dev.typetype.server.services.AndroidDashManifestResult
import dev.typetype.server.services.SabrSessionHolder
import kotlinx.serialization.Serializable

@Serializable
internal data class AndroidPlaybackCreateRequest(
    val videoItag: Int? = null,
    val audioItag: Int? = null,
    val audioTrackId: String? = null,
)

@Serializable
internal data class AndroidPlaybackSeekRequest(
    val generation: Long,
    val playerTimeMs: Long,
)

@Serializable
internal data class AndroidPlaybackResponse(
    val sessionId: String,
    val videoId: String,
    val manifestUrl: String,
    val videoItag: Int,
    val audioItag: Int,
    val audioTrackId: String? = null,
    val generation: Long,
    val ready: Boolean,
    val status: String,
    val retryAfterMs: Long? = null,
)

internal fun SabrSessionHolder.toAndroidPlaybackResponse(
    manifest: AndroidDashManifestResult,
): AndroidPlaybackResponse = AndroidPlaybackResponse(
    sessionId = sessionToken,
    videoId = key.videoId,
    manifestUrl = "/api/android/youtube/playback/$sessionToken/manifest.mpd",
    videoItag = videoFormat.itag,
    audioItag = audioFormat.itag,
    audioTrackId = audioFormat.audioTrackId,
    generation = activeGeneration(),
    ready = manifest is AndroidDashManifestResult.Ready,
    status = if (manifest is AndroidDashManifestResult.Ready) "ready" else "preparing",
    retryAfterMs = if (manifest is AndroidDashManifestResult.Ready) null else ANDROID_RETRY_AFTER_MS,
)

internal const val ANDROID_RETRY_AFTER_MS = 500L
