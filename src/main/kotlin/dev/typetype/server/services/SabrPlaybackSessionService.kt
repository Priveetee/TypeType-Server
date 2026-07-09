package dev.typetype.server.services

import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.slf4j.LoggerFactory

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

    fun seekExisting(holder: SabrSessionHolder, playerTimeMs: Long): SabrPlaybackPreparation {
        val generation = holder.advancePlaybackGeneration(playerTimeMs)
        holder.setRequestedSeekTimeMs(playerTimeMs)
        holder.session.streamState.setSelectVideoFormatBeforeAudio(playerTimeMs > SEEK_FORMAT_ORDER_MS)
        holder.requestReposition(playerTimeMs, generation)
        sessionStore.startPump(holder)
        return SabrPlaybackPreparation(holder, playerTimeMs, ready = false)
    }

    fun lookup(sessionId: String): SabrSessionHolder? = sessionStore.lookupByToken(sessionId)

    fun startPump(holder: SabrSessionHolder): Unit = sessionStore.startPump(holder)

    fun warmPlayback(holder: SabrSessionHolder): Unit = sessionStore.warmPlaybackAsync(holder)

    suspend fun fetchInitialization(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        timeoutMs: Long,
        generation: Long,
    ): SabrPlaybackSegmentResult {
        val activeGeneration = holder.activeGeneration()
        if (generation > activeGeneration) return SabrPlaybackSegmentResult.InvalidGeneration
        val request = SabrSegmentRequest.initialization(format)
        if (generation < activeGeneration) return staleMedia(holder, request)
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
        sessionStore.cachedSegment(holder, request)?.let {
            holder.markServed(it, generation)
            return SabrPlaybackSegmentResult.Ready(it.mimeType, it.bytes)
        }
        holder.requestSegmentDemand(request)
        val segment = sessionStore.fetchPlaybackSegment(holder, format, sequence, timeoutMs)
        return if (segment == null) SabrPlaybackSegmentResult.Retry(holder, REPOSITIONING) else {
            holder.clearSegmentDemand(request)
            SabrPlaybackSegmentResult.Ready(format.mimeType.orEmpty(), segment.data)
        }
    }

    private suspend fun staleMedia(holder: SabrSessionHolder, request: SabrSegmentRequest): SabrPlaybackSegmentResult =
        sessionStore.cachedSegment(holder, request)
            ?.let { SabrPlaybackSegmentResult.Ready(it.mimeType, it.bytes) }
            ?: SabrPlaybackSegmentResult.Stale(holder)

    private suspend fun prepareHolder(holder: SabrSessionHolder, startTimeMs: Long): SabrPlaybackPreparation {
        val startedAt = System.currentTimeMillis()
        holder.setPlayerTimeMs(startTimeMs)
        withTimeoutOrNull(INITIALIZATION_PRELOAD_TIMEOUT_MS) {
            sessionStore.fetchInitializationData(holder, holder.videoFormat)
            sessionStore.fetchInitializationData(holder, holder.audioFormat)
        }
        holder.session.streamState.setSelectVideoFormatBeforeAudio(startTimeMs > SEEK_FORMAT_ORDER_MS)
        if (startTimeMs > 0L) holder.requestReposition(startTimeMs, holder.activeGeneration())
        sessionStore.startPump(holder)
        sessionStore.warmPlaybackAsync(holder)
        logger.info(
            "sabr_playback_prepare videoId={} startTimeMs={} elapsedMs={} ready=false",
            holder.key.videoId,
            startTimeMs,
            System.currentTimeMillis() - startedAt,
        )
        return SabrPlaybackPreparation(holder, startTimeMs, ready = false)
    }

    private fun SabrSessionHolder.matches(audio: YoutubeSabrFormat, video: YoutubeSabrFormat): Boolean =
        audioFormat.itag == audio.itag && audioFormat.audioTrackId == audio.audioTrackId && videoFormat.itag == video.itag

    private fun SabrSessionHolder.requestReposition(playerTimeMs: Long, generation: Long): Unit {
        val request = mediaRequestsAt(playerTimeMs, generation).maxByOrNull {
            session.streamState.getSegmentStartMs(it.format, it.sequenceNumber)
        } ?: return
        val startMs = session.streamState.getSegmentStartMs(request.format, request.sequenceNumber).coerceAtLeast(0L)
        setReaderPosition(request.format, startMs, generation)
        if (startMs < session.streamState.getMinBufferedEndMs()) requestRefetch(request) else requestForwardSeek(request)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SabrPlaybackSessionService::class.java)
        const val PREPARING = "preparing"
        const val REPOSITIONING = "repositioning"
        const val INITIALIZATION_PRELOAD_TIMEOUT_MS = 2_000L
        const val SEEK_FORMAT_ORDER_MS = 1_000L
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
