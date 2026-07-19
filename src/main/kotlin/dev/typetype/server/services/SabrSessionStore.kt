package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.time.Duration
import java.time.Instant

internal class SabrSessionStore(
    tokenServiceUrl: String,
    private val maxSessions: Int = SabrSessionStoreDefaults.maxSessions(),
    private val idleEviction: Duration = SabrSessionStoreDefaults.idleEviction(),
    private val pumpLoopIntervalMs: Long = SabrPumpPolicy.IDLE_POLL_MS,
    private val tokenClient: TypetypeTokenSabrTokenClient = TypetypeTokenSabrTokenClient(tokenServiceUrl),
    private val sessionClient: TypetypeTokenYoutubeSessionClient = TypetypeTokenYoutubeSessionClient(tokenServiceUrl),
    private val initCache: CacheService? = null,
) {
    private val registry = SabrSessionRegistry()
    private val segmentCache = SabrSegmentCache()
    private val pump = SabrSessionPump(segmentCache) { videoId ->
        tokenClient.fetch(videoId, refreshVideo = true)
    }
    private val warmer = SabrPlaybackWarmer()
    private val infoFetcher = SabrInfoFetcher(tokenClient, sessionClient, sharedCache = initCache)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val idleCheckJob: Job = scope.launch { idleEvictionLoop() }

    init {
        runCatching { NewPipeInitializer.init() }
    }
    internal fun getOrCreate(
        videoId: String,
        userId: String,
        info: YoutubeSabrInfo,
        audioFormat: YoutubeSabrFormat,
        videoFormat: YoutubeSabrFormat,
        initialToken: SabrTokenBundle? = null,
        startTimeMs: Long = 0L,
        startPump: Boolean = true,
        purpose: SabrSessionPurpose = SabrSessionPurpose.MANIFEST,
        audioOnly: Boolean = false,
    ): SabrSessionHolder {
        val playbackToken = if (purpose == SabrSessionPurpose.PLAYBACK) SabrSessionTokenGenerator.newToken() else null
        val key = SabrSessionKey(
            videoId,
            userId,
            audioFormat.itag,
            audioFormat.audioTrackId,
            videoFormat.itag,
            startTimeMs.coerceAtLeast(0L),
            purpose,
            audioOnly,
            playbackToken,
        )
        registry.getReusable(key)?.let { return it }
        registry.ensureCapacity(maxSessions)
        val provider = TypetypeTokenSabrPoTokenProvider(tokenClient, initialToken)
        val session = YoutubeSabrSession(info, audioFormat, videoFormat, provider)
        val normalizedStartTimeMs = startTimeMs.coerceAtLeast(0L)
        session.streamState.setPlayerTimeMs(normalizedStartTimeMs)
        runCatching { provider.getPoToken(info, session.streamState) }
            .getOrNull()
            ?.let { session.streamState.setPoToken(it) }
        val holder = SabrSessionHolder(
            session,
            info,
            audioFormat,
            videoFormat,
            playbackToken ?: SabrSessionTokenGenerator.newToken(),
            key,
            Instant.now(),
            initialToken,
        )
        holder.setPlayerTimeMs(normalizedStartTimeMs)
        registry.put(key, holder)
        if (startPump) startPump(holder)
        return holder
    }
    internal fun startPump(holder: SabrSessionHolder) {
        scope.launchSabrPump(pump, registry, holder, pumpLoopIntervalMs)
    }

    internal fun warmPlaybackAsync(holder: SabrSessionHolder) {
        if (holder.playerTimeMs() > 0L) return
        if (holder.playbackState() == SabrPlaybackState.REQUESTING || holder.playbackState() == SabrPlaybackState.REPOSITIONING) return
        scope.launch { fetchMediaAt(holder, holder.playerTimeMs()) }
    }

    internal fun warmInitializationAsync(holder: SabrSessionHolder): Unit =
        SabrInitializationPolicy.warmFormats(holder.key.audioOnly, holder.audioFormat, holder.videoFormat).forEach { format ->
            scope.launch { fetchDirectInitialization(holder, format) }
        }

    internal fun lookup(videoId: String, userId: String, audioItag: Int, videoItag: Int): SabrSessionHolder? =
        registry.get(SabrSessionKey(videoId, userId, audioItag, null, videoItag, 0L))

    internal fun lookupByItag(videoId: String, userId: String, itag: Int): SabrSessionHolder? =
        registry.lookupByItag(videoId, userId, itag)

    internal fun lookupByToken(videoId: String, token: String, itag: Int): SabrSessionHolder? = registry.lookupByToken(videoId, token, itag)

    internal fun lookupByToken(videoId: String, token: String): SabrSessionHolder? = registry.lookupByToken(videoId, token)

    internal fun lookupByToken(token: String): SabrSessionHolder? = registry.lookupByToken(token)

    internal suspend fun ensureWarmed(holder: SabrSessionHolder, maxPumps: Int = 8): Unit = pump.ensureWarmed(holder, maxPumps)

    internal suspend fun preflightPlayback(holder: SabrSessionHolder, playerTimeMs: Long): Boolean =
        warmer.preflight(this, holder, playerTimeMs)

    internal suspend fun cachedMediaAt(holder: SabrSessionHolder, playerTimeMs: Long): List<CachedSabrSegment>? =
        holder.mediaRequestsAt(playerTimeMs).map { segmentCache.get(holder, it) }
            .takeIf { cached -> cached.all { it != null } }?.filterNotNull()

    internal suspend fun cachedSegment(holder: SabrSessionHolder, request: SabrSegmentRequest): CachedSabrSegment? {
        holder.session.getCachedSegment(request)?.let {
            segmentCache.put(holder, it)
            holder.clearSegmentDemand(request)
            return segmentCache.get(holder, request)
        }
        return segmentCache.get(holder, request)?.also { holder.clearSegmentDemand(request) }
    }

    internal fun requestSegmentDemand(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        generation: Long,
    ): Unit {
        holder.requestSegmentDemand(request, generation)
        startPump(holder)
    }

    internal suspend fun fetchInfo(
        videoId: String,
        startTimeMs: Long = 0L,
        cachedFirst: Boolean = false,
    ): SabrPreparedInfo? = infoFetcher.fetchInfo(videoId, startTimeMs, cachedFirst)

    internal suspend fun rememberExtractedInfo(videoId: String, info: YoutubeSabrInfo): Unit =
        infoFetcher.rememberExtractedInfo(videoId, info)

    internal suspend fun invalidatePlaybackInfo(videoId: String): Unit = infoFetcher.invalidatePlayback(videoId)

    internal suspend fun fetchSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
    ): SabrMediaSegment? = pump.fetchSegment(holder, request)

    internal suspend fun fetchMediaAt(holder: SabrSessionHolder, playerTimeMs: Long): List<SabrMediaSegment>? =
        pump.fetchMediaAt(holder, playerTimeMs)

    internal suspend fun fetchInitializationData(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): ByteArray? {
        if (SabrInitializationPolicy.requiresVideoFirst(holder.key.audioOnly, format.isAudio, holder.playerTimeMs())) {
            fetchInitializationSegmentData(holder, holder.videoFormat)
        }
        return fetchInitializationSegmentData(holder, format)
    }

    private suspend fun fetchInitializationSegmentData(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): ByteArray? {
        holder.liveInitialization(format)?.let { return it }
        val request = SabrSegmentRequest.initialization(format)
        holder.session.getCachedSegment(request)?.let { segmentCache.put(holder, it); return it.data }
        SabrInitializationData.fetch(holder.key.videoId, format, initCache)?.let {
            holder.session.streamState.ingestInitializationData(format, it)
            return it
        }
        SabrInitializationData.bootstrap(holder, format, initCache)?.let { return it }
        val segment = pump.fetchSegment(holder, request) ?: return null
        segmentCache.put(holder, segment)
        SabrInitializationData.remember(holder.key.videoId, format, segment.data, initCache)
        return segment.data
    }

    private suspend fun fetchDirectInitialization(holder: SabrSessionHolder, format: YoutubeSabrFormat): Unit {
        val data = SabrInitializationData.fetch(holder.key.videoId, format, initCache) ?: return
        holder.pumpMutex.withLock { holder.session.streamState.ingestInitializationData(format, data) }
    }

    fun release() {
        idleCheckJob.cancel()
        scope.cancel()
        registry.clear()
    }

    internal fun release(holder: SabrSessionHolder): Unit = registry.remove(holder)

    private suspend fun idleEvictionLoop() {
        while (true) {
            delay(15_000)
            registry.evictIdle(Instant.now().minus(idleEviction))
        }
    }
}
