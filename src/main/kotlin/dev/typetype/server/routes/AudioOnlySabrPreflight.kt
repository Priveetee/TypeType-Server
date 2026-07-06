package dev.typetype.server.routes

import dev.typetype.server.services.AudioOnlyStreamSelection
import dev.typetype.server.services.SabrSessionStore
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun SabrSessionStore.isSabrAudioOnlyPlayable(
    videoUrl: String,
    userId: String?,
    selection: AudioOnlyStreamSelection,
): Boolean = sabrAudioOnlyUnplayableReason(videoUrl, userId, selection) == null

internal suspend fun SabrSessionStore.sabrAudioOnlyUnplayableReason(
    videoUrl: String,
    userId: String?,
    selection: AudioOnlyStreamSelection,
): String? {
    return withTimeoutOrNull(SABR_AUDIO_ONLY_PREFLIGHT_TIMEOUT_MS) {
        sabrAudioOnlyUnplayableReasonWithinTimeout(videoUrl, userId, selection)
    } ?: "SABR audio-only preflight timed out before complete audio body"
}

private suspend fun SabrSessionStore.sabrAudioOnlyUnplayableReasonWithinTimeout(
    videoUrl: String,
    userId: String?,
    selection: AudioOnlyStreamSelection,
): String? {
    val videoId = videoUrl.youtubeVideoId() ?: return "Invalid YouTube URL"
    if (AudioOnlySabrBodyCache.get(videoId, selection.stream.itag, selection.stream.audioTrackId) != null) {
        return null
    }
    val prepared = fetchInfo(videoId, cachedFirst = true) ?: return "SABR probe failed"
    val audio = SabrFormatSelector.audio(prepared.info, selection.stream.itag, selection.stream.audioTrackId, requireAac = true)
        ?: return "No SABR audio for this video"
    val video = SabrFormatSelector.lightestVideo(prepared.info) ?: return "No SABR video context for this audio"
    val holder = getOrCreate(
        videoId,
        userId ?: videoId,
        prepared.info,
        audio,
        video,
        prepared.initialToken,
        startPump = false,
    )
    holder.setActiveTracks(videoActive = true, audioActive = true)
    val init = fetchInitializationData(holder, audio) ?: return "SABR audio initialization failed"
    val body = materializeSabrAudioOnlyBody(this, holder, init) ?: return "SABR audio-only body is incomplete"
    AudioOnlySabrBodyCache.put(videoId, audio.itag, audio.audioTrackId, body)
    return null
}

private const val SABR_AUDIO_ONLY_PREFLIGHT_TIMEOUT_MS = 25_000L
