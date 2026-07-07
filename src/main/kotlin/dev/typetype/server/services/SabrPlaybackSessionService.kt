package dev.typetype.server.services

import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
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
    ): SabrPlaybackPreparation {
        if (source.matches(audio, video)) return seekExisting(source, playerTimeMs)
        return prepare(
            videoId = source.key.videoId,
            userId = source.key.userId,
            prepared = prepared,
            audio = audio,
            video = video,
            startTimeMs = playerTimeMs,
        )
    }

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
        generation: Long,
    ): SabrPlaybackSegmentResult {
        if (sequence < 1) return SabrPlaybackSegmentResult.InvalidSequence
        val activeGeneration = holder.activeGeneration()
        if (generation > activeGeneration) return SabrPlaybackSegmentResult.InvalidGeneration
        val request = SabrSegmentRequest.media(format, sequence)
        if (generation < activeGeneration) return staleMedia(holder, request)
        val segment = sessionStore.fetchPlaybackSegment(holder, format, sequence, timeoutMs)
        return if (segment == null) SabrPlaybackSegmentResult.Retry(holder, REPOSITIONING) else {
            SabrPlaybackSegmentResult.Ready(format.mimeType.orEmpty(), segment.data)
        }
    }

    private suspend fun staleMedia(holder: SabrSessionHolder, request: SabrSegmentRequest): SabrPlaybackSegmentResult =
        sessionStore.cachedSegment(holder, request)
            ?.let { SabrPlaybackSegmentResult.Ready(it.mimeType, it.bytes) }
            ?: SabrPlaybackSegmentResult.Stale(holder)

    private suspend fun seekExisting(holder: SabrSessionHolder, playerTimeMs: Long): SabrPlaybackPreparation {
        val generation = holder.advancePlaybackGeneration(playerTimeMs)
        holder.requestReposition(playerTimeMs, generation)
        return prepareHolder(holder, playerTimeMs)
    }

    private suspend fun prepareHolder(holder: SabrSessionHolder, startTimeMs: Long): SabrPlaybackPreparation {
        holder.setPlayerTimeMs(startTimeMs)
        val ready = withTimeoutOrNull(PLAYBACK_READY_TIMEOUT_MS) {
            sessionStore.preflightPlayback(holder, startTimeMs)
        } == true
        sessionStore.startPump(holder)
        return SabrPlaybackPreparation(holder, startTimeMs, ready)
    }

    private fun SabrSessionHolder.matches(audio: YoutubeSabrFormat, video: YoutubeSabrFormat): Boolean =
        audioFormat.itag == audio.itag && audioFormat.audioTrackId == audio.audioTrackId && videoFormat.itag == video.itag

    private fun SabrSessionHolder.requestReposition(playerTimeMs: Long, generation: Long): Unit {
        val request = mediaRequestsAt(playerTimeMs, generation).firstOrNull() ?: return
        val startMs = session.streamState.getSegmentStartMs(request.format, request.sequenceNumber).coerceAtLeast(0L)
        setReaderPosition(request.format, startMs, generation)
        if (startMs < session.streamState.getMinBufferedEndMs()) requestRefetch(request) else requestForwardSeek(request)
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
    data class Stale(val holder: SabrSessionHolder) : SabrPlaybackSegmentResult()
    data object InvalidSequence : SabrPlaybackSegmentResult()
    data object InvalidGeneration : SabrPlaybackSegmentResult()
}
