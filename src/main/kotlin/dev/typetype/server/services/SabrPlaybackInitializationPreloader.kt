package dev.typetype.server.services

import kotlinx.coroutines.withTimeoutOrNull

internal data class SabrPlaybackInitializationPreloadResult(
    val video: ByteArray?,
    val audio: ByteArray?,
) {
    fun isComplete(audioOnly: Boolean): Boolean =
        audio != null && (audioOnly || video != null)

    fun missingTracks(audioOnly: Boolean, videoItag: Int, audioItag: Int): String =
        buildList {
            if (!audioOnly && video == null) add("video:$videoItag")
            if (audio == null) add("audio:$audioItag")
        }.joinToString()
}

internal object SabrPlaybackInitializationPreloader {
    suspend fun preload(
        sessionStore: SabrSessionStore,
        holder: SabrSessionHolder,
        audioOnly: Boolean,
        timeoutMs: Long,
    ): SabrPlaybackInitializationPreloadResult = withTimeoutOrNull(timeoutMs) {
        val video = holder.videoFormat
            .takeUnless { audioOnly }
            ?.let { sessionStore.fetchInitializationData(holder, it) }
        val audio = sessionStore.fetchInitializationData(holder, holder.audioFormat)
        SabrPlaybackInitializationPreloadResult(video, audio)
    } ?: SabrPlaybackInitializationPreloadResult(null, null)
}
