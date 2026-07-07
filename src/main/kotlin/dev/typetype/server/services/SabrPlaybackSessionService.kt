package dev.typetype.server.services

import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class SabrPlaybackSessionService(private val sessionStore: SabrSessionStore) {
    suspend fun prepare(
        videoId: String,
        userId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        startTimeMs: Long,
    ): SabrPlaybackPreparation {
        val holder = sessionStore.getOrCreate(
            videoId = videoId,
            userId = userId,
            info = prepared.info,
            audioFormat = audio,
            videoFormat = video,
            initialToken = prepared.initialToken,
            startTimeMs = startTimeMs,
            startPump = false,
        )
        return prepareHolder(holder, startTimeMs)
    }

    suspend fun seek(
        source: SabrSessionHolder,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        playerTimeMs: Long,
    ): SabrPlaybackPreparation = prepare(
        videoId = source.key.videoId,
        userId = source.key.userId,
        prepared = prepared,
        audio = audio,
        video = video,
        startTimeMs = playerTimeMs,
    )

    fun lookup(sessionId: String): SabrSessionHolder? = sessionStore.lookupByToken(sessionId)

    fun startPump(holder: SabrSessionHolder): Unit = sessionStore.startPump(holder)

    suspend fun fetchInitialization(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        timeoutMs: Long,
    ): SabrPlaybackSegmentResult {
        val bytes = withTimeoutOrNull(timeoutMs) { sessionStore.fetchInitializationData(holder, format) }
        return if (bytes == null) SabrPlaybackSegmentResult.Retry(holder, PREPARING) else {
            SabrPlaybackSegmentResult.Ready(format.mimeType.orEmpty(), bytes)
        }
    }

    suspend fun fetchMedia(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        sequence: Int,
        timeoutMs: Long,
    ): SabrPlaybackSegmentResult {
        if (sequence < 1) return SabrPlaybackSegmentResult.InvalidSequence
        val segment = sessionStore.fetchPlaybackSegment(holder, format, sequence, timeoutMs)
        return if (segment == null) SabrPlaybackSegmentResult.Retry(holder, REPOSITIONING) else {
            SabrPlaybackSegmentResult.Ready(format.mimeType.orEmpty(), segment.data)
        }
    }

    private suspend fun prepareHolder(holder: SabrSessionHolder, startTimeMs: Long): SabrPlaybackPreparation {
        holder.setPlayerTimeMs(startTimeMs)
        val ready = withTimeoutOrNull(PLAYBACK_READY_TIMEOUT_MS) {
            sessionStore.preflightPlayback(holder, startTimeMs)
        } == true
        sessionStore.startPump(holder)
        return SabrPlaybackPreparation(holder, startTimeMs, ready)
    }

    private companion object {
        const val PLAYBACK_READY_TIMEOUT_MS = 20_000L
        const val PREPARING = "preparing"
        const val REPOSITIONING = "repositioning"
    }
}

internal data class SabrPlaybackPreparation(
    val holder: SabrSessionHolder,
    val startTimeMs: Long,
    val ready: Boolean,
)

internal sealed class SabrPlaybackSegmentResult {
    data class Ready(val mimeType: String, val bytes: ByteArray) : SabrPlaybackSegmentResult()
    data class Retry(val holder: SabrSessionHolder, val status: String) : SabrPlaybackSegmentResult()
    data object InvalidSequence : SabrPlaybackSegmentResult()
}
