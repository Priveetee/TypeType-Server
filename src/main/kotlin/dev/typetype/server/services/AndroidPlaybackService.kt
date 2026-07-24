package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class AndroidPlaybackService(
    private val store: SabrSessionStore,
    private val sessions: AndroidPlaybackSessionRegistry = AndroidPlaybackSessionRegistry(store),
    private val manifests: AndroidDashManifestService = AndroidDashManifestService(),
    private val preparation: AndroidPlaybackPreparationCoordinator =
        AndroidPlaybackPreparationCoordinator(store, manifests),
) {
    private val playback = SabrPlaybackSessionService(store, SabrSessionPurpose.ANDROID_PLAYBACK)

    suspend fun create(
        videoId: String,
        userId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        subtitles: List<AndroidSubtitleTrack>,
    ): AndroidPlaybackCreateResult {
        if (prepared.isLive || prepared.isLiveContent) return AndroidPlaybackCreateResult.UnsupportedLive
        val result = playback.prepare(
            videoId = videoId,
            userId = userId,
            prepared = prepared,
            audio = audio,
            video = video,
            startTimeMs = 0L,
            preloadInitialization = false,
            startPumpOnPrepare = false,
        )
        val session = sessions.register(result.holder, subtitles)
        preparation.start(session)
        return AndroidPlaybackCreateResult.Created(session, session.preparation.result(result.holder, manifests))
    }

    fun lookup(sessionId: String): AndroidPlaybackSessionLookup = sessions.lookup(sessionId)

    fun seek(
        session: AndroidPlaybackSession,
        generation: Long,
        playerTimeMs: Long,
    ): AndroidPlaybackSeekResult {
        val holder = session.holder
        if (generation != holder.activeGeneration()) return AndroidPlaybackSeekResult.StaleGeneration
        playback.seekExisting(holder, playerTimeMs.coerceAtLeast(0L))
        return AndroidPlaybackSeekResult.Ready(holder, session.preparation.result(holder, manifests))
    }

    fun manifest(session: AndroidPlaybackSession): AndroidDashManifestResult =
        session.preparation.result(session.holder, manifests)

    suspend fun initialization(
        holder: SabrSessionHolder,
        itag: Int,
        generation: Long,
    ): AndroidPlaybackMediaResult {
        val format = holder.formatForItag(itag) ?: return AndroidPlaybackMediaResult.TrackNotFound
        if (generation != holder.activeGeneration()) return AndroidPlaybackMediaResult.StaleGeneration
        return playback.fetchInitialization(holder, format, MEDIA_TIMEOUT_MS, generation).toAndroidResult()
    }

    suspend fun segment(
        holder: SabrSessionHolder,
        itag: Int,
        sequence: Int,
        generation: Long,
    ): AndroidPlaybackMediaResult {
        val format = holder.formatForItag(itag) ?: return AndroidPlaybackMediaResult.TrackNotFound
        if (generation != holder.activeGeneration()) return AndroidPlaybackMediaResult.StaleGeneration
        return playback.fetchMedia(holder, format, sequence, MEDIA_TIMEOUT_MS, generation).toAndroidResult()
    }

    private fun SabrSessionHolder.formatForItag(itag: Int): YoutubeSabrFormat? = when (itag) {
        audioFormat.itag -> audioFormat
        videoFormat.itag -> videoFormat
        else -> null
    }

    private fun SabrPlaybackSegmentResult.toAndroidResult(): AndroidPlaybackMediaResult = when (this) {
        is SabrPlaybackSegmentResult.Ready -> AndroidPlaybackMediaResult.Ready(mimeType, bytes)
        is SabrPlaybackSegmentResult.Retry -> AndroidPlaybackMediaResult.Preparing
        is SabrPlaybackSegmentResult.Stale -> AndroidPlaybackMediaResult.StaleGeneration
        SabrPlaybackSegmentResult.InvalidGeneration -> AndroidPlaybackMediaResult.StaleGeneration
        SabrPlaybackSegmentResult.InvalidSequence -> AndroidPlaybackMediaResult.InvalidSequence
    }

    private companion object {
        const val MEDIA_TIMEOUT_MS = 4_000L
    }
}

internal sealed interface AndroidPlaybackCreateResult {
    data class Created(
        val session: AndroidPlaybackSession,
        val manifest: AndroidDashManifestResult,
    ) : AndroidPlaybackCreateResult

    data object UnsupportedLive : AndroidPlaybackCreateResult
}

internal sealed interface AndroidPlaybackSeekResult {
    data class Ready(
        val holder: SabrSessionHolder,
        val manifest: AndroidDashManifestResult,
    ) : AndroidPlaybackSeekResult

    data object StaleGeneration : AndroidPlaybackSeekResult
}

internal sealed interface AndroidPlaybackMediaResult {
    data class Ready(val mimeType: String, val bytes: ByteArray) : AndroidPlaybackMediaResult
    data object Preparing : AndroidPlaybackMediaResult
    data object StaleGeneration : AndroidPlaybackMediaResult
    data object TrackNotFound : AndroidPlaybackMediaResult
    data object InvalidSequence : AndroidPlaybackMediaResult
}
