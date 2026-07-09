package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class SabrPlaybackColdSeekPreparer(private val sessionStore: SabrSessionStore) {
    suspend fun prepare(
        holder: SabrSessionHolder,
        videoId: String,
        userId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        startTimeMs: Long,
        timeoutMs: Long,
    ): SabrSessionHolder {
        val (videoInit, audioInit) = SabrPlaybackInitializationPreloader.preload(sessionStore, holder, timeoutMs)
        if (videoInit != null && audioInit != null) {
            SabrInitializationData.remember(video, videoInit)
            SabrInitializationData.remember(audio, audioInit)
            holder.session.streamState.ingestInitializationData(video, videoInit)
            holder.session.streamState.ingestInitializationData(audio, audioInit)
        }
        if (holder.session.requestNumber == 0) return holder
        if (videoInit == null || audioInit == null) return holder
        sessionStore.release(holder)
        val fresh = sessionStore.getOrCreate(
            videoId = videoId,
            userId = userId,
            info = prepared.info,
            audioFormat = audio,
            videoFormat = video,
            initialToken = prepared.initialToken,
            startTimeMs = startTimeMs,
            startPump = false,
        )
        fresh.session.streamState.ingestInitializationData(video, videoInit)
        fresh.session.streamState.ingestInitializationData(audio, audioInit)
        return fresh
    }
}
