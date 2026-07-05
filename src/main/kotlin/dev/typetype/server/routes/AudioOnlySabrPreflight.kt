package dev.typetype.server.routes

import dev.typetype.server.services.AudioOnlyStreamSelection
import dev.typetype.server.services.SabrSessionStore

internal suspend fun SabrSessionStore.isSabrAudioOnlyPlayable(
    videoUrl: String,
    userId: String?,
    selection: AudioOnlyStreamSelection,
): Boolean {
    val videoId = videoUrl.youtubeVideoId() ?: return false
    val prepared = fetchInfo(videoId, cachedFirst = true) ?: return false
    val audio = SabrFormatSelector.audio(prepared.info, selection.stream.itag, selection.stream.audioTrackId, requireAac = true)
        ?: return false
    val video = SabrFormatSelector.video(prepared.info, null) ?: return false
    val holder = getOrCreate(
        videoId,
        userId ?: videoId,
        prepared.info,
        audio,
        video,
        prepared.initialToken,
        startPump = false,
    )
    holder.setActiveTracks(videoActive = false, audioActive = true)
    val init = fetchInitializationData(holder, audio) ?: return false
    return materializeSabrAudioOnlyBody(this, holder, init) != null
}
