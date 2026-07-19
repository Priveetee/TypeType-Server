package dev.typetype.server.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
        audioOnly: Boolean = false,
        isLive: Boolean = false,
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
            purpose = SabrSessionPurpose.PLAYBACK,
            audioOnly = audioOnly,
        )
        if (isLive || prepared.isLive || prepared.isLiveContent) holder.markExpectedLive()
        if (holder.expectsLive()) {
            holder.setActiveTracks(videoActive = !audioOnly, audioActive = true)
            holder.session.streamState.setSelectVideoFormatBeforeAudio(!audioOnly)
            sessionStore.ensureWarmed(holder, LIVE_INITIAL_PUMPS)
        } else {
            SabrPlaybackInitializationPreloader.preload(sessionStore, holder, INITIALIZATION_PRELOAD_TIMEOUT_MS)
        }
        return prepareHolder(holder, holder.resolvePlaybackStartMs(startTimeMs), audioOnly)
    }

    suspend fun seek(
        source: SabrSessionHolder,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        playerTimeMs: Long,
        audioOnly: Boolean = false,
    ): SabrPlaybackPreparation {
        if (source.matches(audio, video)) return seekExisting(source, playerTimeMs, audioOnly)
        return prepare(
            videoId = source.key.videoId,
            userId = source.key.userId,
            prepared = prepared,
            audio = audio,
            video = video,
            startTimeMs = playerTimeMs,
            audioOnly = audioOnly,
            isLive = source.expectsLive() || prepared.isLive || prepared.isLiveContent,
        )
    }

    fun seekExisting(
        holder: SabrSessionHolder,
        playerTimeMs: Long,
        audioOnly: Boolean = false,
    ): SabrPlaybackPreparation = synchronized(holder) {
        holder.clearSegmentDemands()
        val generation = holder.advancePlaybackGeneration(playerTimeMs)
        holder.setActiveTracks(videoActive = !audioOnly, audioActive = true)
        holder.setRequestedSeekTimeMs(playerTimeMs)
        holder.session.streamState.setSelectVideoFormatBeforeAudio(playerTimeMs > SEEK_FORMAT_ORDER_MS)
        holder.requestReposition(playerTimeMs, generation)
        sessionStore.startPump(holder)
        SabrPlaybackPreparation(holder, playerTimeMs, ready = false)
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
        sessionStore.requestSegmentDemand(holder, request, generation)
        val segment = awaitCachedSegment(holder, request, timeoutMs)
        return if (segment == null) SabrPlaybackSegmentResult.Retry(holder, REPOSITIONING) else {
            holder.clearSegmentDemand(request)
            holder.markServed(segment, generation)
            SabrPlaybackSegmentResult.Ready(segment.mimeType, segment.bytes)
        }
    }

    private suspend fun awaitCachedSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        timeoutMs: Long,
    ): CachedSabrSegment? = withTimeoutOrNull(timeoutMs) {
        var segment = sessionStore.cachedSegment(holder, request)
        while (segment == null && holder.terminalFailure() == null && holder.networkFailure() == null) {
            delay(SEGMENT_WAIT_MS)
            segment = sessionStore.cachedSegment(holder, request)
            if (segment == null && holder.livePlaybackSnapshot()?.active == true) {
                segment = sessionStore.findCachedPlaybackMediaAt(
                    holder = holder,
                    format = request.format,
                    targetMs = holder.playbackSegmentStartMs(request.format, request.sequenceNumber),
                    predictedSequence = request.sequenceNumber,
                    allowFollowing = true,
                )
            }
        }
        segment
    }

    private suspend fun staleMedia(holder: SabrSessionHolder, request: SabrSegmentRequest): SabrPlaybackSegmentResult =
        sessionStore.cachedSegment(holder, request)
            ?.let { SabrPlaybackSegmentResult.Ready(it.mimeType, it.bytes) }
            ?: SabrPlaybackSegmentResult.Stale(holder)

    private suspend fun prepareHolder(
        holder: SabrSessionHolder,
        startTimeMs: Long,
        audioOnly: Boolean,
    ): SabrPlaybackPreparation {
        val startedAt = System.currentTimeMillis()
        holder.setActiveTracks(videoActive = !audioOnly, audioActive = true)
        holder.setPlayerTimeMs(startTimeMs)
        holder.session.streamState.setSelectVideoFormatBeforeAudio(startTimeMs > SEEK_FORMAT_ORDER_MS)
        if (startTimeMs > SEEK_FORMAT_ORDER_MS) holder.anchorReaderPositions(startTimeMs)
        if (startTimeMs > 0L) {
            holder.setRequestedSeekTimeMs(startTimeMs)
            holder.requestReposition(startTimeMs, holder.activeGeneration())
        }
        sessionStore.startPump(holder)
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

    private fun SabrSessionHolder.requestReposition(
        playerTimeMs: Long,
        generation: Long,
    ): Unit {
        val targetFormat = if (isVideoActive()) videoFormat else audioFormat
        val sequence = playbackStartSequence(targetFormat, playerTimeMs)
        val request = SabrSegmentRequest.media(targetFormat, sequence)
        val companion = audioFormat.takeIf { isVideoActive() }?.let { format ->
            SabrSegmentRequest.media(format, playbackStartSequence(format, playerTimeMs))
        }
        val targets = listOfNotNull(request, companion)
        targets.forEach { target ->
            val targetStartMs = playbackSegmentStartMs(target.format, target.sequenceNumber)
            setReaderPosition(target.format, targetStartMs, generation)
        }
        val missing = targets.filter { session.getCachedSegment(it) == null }
        if (missing.isEmpty()) return
        missing.forEach { requestSegmentDemand(it, generation) }
        if (livePlaybackSnapshot()?.active == true) return
        val anchor = missing.first()
        val startMs = playbackSegmentStartMs(anchor.format, anchor.sequenceNumber)
        if (startMs < session.streamState.getMinBufferedEndMs()) {
            requestRefetch(anchor)
        } else {
            requestForwardSeek(anchor)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SabrPlaybackSessionService::class.java)
        const val PREPARING = "preparing"
        const val REPOSITIONING = "repositioning"
        const val INITIALIZATION_PRELOAD_TIMEOUT_MS = 6_000L
        const val SEEK_FORMAT_ORDER_MS = 1_000L
        const val SEGMENT_WAIT_MS = 250L
        const val LIVE_INITIAL_PUMPS = 8
    }
}
