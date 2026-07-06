package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

internal class SabrSessionStore(
    tokenServiceUrl: String,
    private val maxSessions: Int = SabrSessionStoreDefaults.maxSessions(),
    private val idleEviction: Duration = SabrSessionStoreDefaults.idleEviction(),
    private val pumpLoopIntervalMs: Long = 750,
    private val tokenClient: TypetypeTokenSabrTokenClient = TypetypeTokenSabrTokenClient(tokenServiceUrl),
    private val initCache: CacheService? = null,
) {
    private val registry = SabrSessionRegistry()
    private val segmentCache = SabrSegmentCache(initCache)
    private val pump = SabrSessionPump(segmentCache)
    private val warmer = SabrPlaybackWarmer()
    private val infoFetcher = SabrInfoFetcher(tokenClient)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val idleCheckJob: Job = scope.launch { idleEvictionLoop() }
    private val random = SecureRandom()

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
    ): SabrSessionHolder {
        val key = SabrSessionKey(
            videoId,
            userId,
            audioFormat.itag,
            audioFormat.audioTrackId,
            videoFormat.itag,
            startTimeMs.coerceAtLeast(0L),
        )
        registry.get(key)?.let { return it }
        registry.ensureCapacity(maxSessions)
        val provider = TypetypeTokenSabrPoTokenProvider(tokenClient, initialToken)
        val session = YoutubeSabrSession(info, audioFormat, videoFormat, provider)
        val normalizedStartTimeMs = startTimeMs.coerceAtLeast(0L)
        session.streamState.setPlayerTimeMs(normalizedStartTimeMs)
        runCatching { provider.getPoToken(info, session.streamState) }
            .getOrNull()
            ?.let { session.streamState.setPoToken(it) }
        val holder = SabrSessionHolder(session, info, audioFormat, videoFormat, newSessionToken(), key, Instant.now())
        holder.setPlayerTimeMs(normalizedStartTimeMs)
        registry.put(key, holder)
        if (startPump) startPump(holder)
        return holder
    }

    internal fun startPump(holder: SabrSessionHolder) {
        if (holder.markPumpStarted()) {
            scope.launch { pump.pumpLoop({ registry.contains(holder.key) }, holder, pumpLoopIntervalMs) }
        }
    }

    internal fun lookup(videoId: String, userId: String, audioItag: Int, videoItag: Int): SabrSessionHolder? =
        registry.get(SabrSessionKey(videoId, userId, audioItag, null, videoItag, 0L))

    internal fun lookupByItag(videoId: String, userId: String, itag: Int): SabrSessionHolder? =
        registry.lookupByItag(videoId, userId, itag)

    internal fun lookupByToken(videoId: String, token: String, itag: Int): SabrSessionHolder? = registry.lookupByToken(videoId, token, itag)

    internal fun lookupByToken(videoId: String, token: String): SabrSessionHolder? = registry.lookupByToken(videoId, token)

    internal suspend fun ensureWarmed(holder: SabrSessionHolder, maxPumps: Int = 8): Unit = pump.ensureWarmed(holder, maxPumps)

    internal suspend fun preflightPlayback(holder: SabrSessionHolder, playerTimeMs: Long): Boolean =
        warmer.preflight(this, holder, playerTimeMs)

    internal suspend fun cachedMediaAt(holder: SabrSessionHolder, playerTimeMs: Long): List<CachedSabrSegment>? =
        holder.mediaRequestsAt(playerTimeMs).map { segmentCache.get(holder, it) }
            .takeIf { cached -> cached.all { it != null } }?.filterNotNull()

    internal suspend fun cachedSegment(holder: SabrSessionHolder, request: SabrSegmentRequest): CachedSabrSegment? =
        segmentCache.get(holder, request)

    internal suspend fun fetchInfo(
        videoId: String,
        startTimeMs: Long = 0L,
        cachedFirst: Boolean = false,
    ): SabrPreparedInfo? = infoFetcher.fetchInfo(videoId, startTimeMs, cachedFirst)

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
        if (format.isAudio && holder.playerTimeMs() > 0L) {
            fetchInitializationSegmentData(holder, holder.videoFormat)
        }
        return fetchInitializationSegmentData(holder, format)
    }

    private suspend fun fetchInitializationSegmentData(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): ByteArray? {
        val request = SabrSegmentRequest.initialization(format)
        holder.session.getCachedSegment(request)?.let { segmentCache.put(holder, it); return it.data }
        SabrInitializationData.fetch(format, initCache)?.let { bytes ->
            holder.session.streamState.ingestInitializationData(format, bytes)
            return bytes
        }
        return pump.fetchSegment(holder, request)?.also { segmentCache.put(holder, it) }?.data
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

    private fun newSessionToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

}
