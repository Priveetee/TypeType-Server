package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

internal class SabrSessionStore(
    tokenServiceUrl: String,
    private val maxSessions: Int = defaultMaxSessions(),
    private val idleEviction: Duration = defaultIdleEviction(),
    private val pumpLoopIntervalMs: Long = 750,
    private val tokenClient: TypetypeTokenSabrTokenClient = TypetypeTokenSabrTokenClient(tokenServiceUrl),
    private val initCache: CacheService? = null,
) {
    private val registry = SabrSessionRegistry()
    private val pump = SabrSessionPump()
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
        runCatching { provider.getPoToken(info, session.streamState) }
            .getOrNull()
            ?.let { session.streamState.setPoToken(it) }
        val holder = SabrSessionHolder(session, info, audioFormat, videoFormat, newSessionToken(), Instant.now())
        registry.put(key, holder)
        scope.launch { pump.pumpLoop({ registry.contains(key) }, holder, pumpLoopIntervalMs) }
        return holder
    }

    internal fun lookup(videoId: String, userId: String, audioItag: Int, videoItag: Int): SabrSessionHolder? =
        registry.get(SabrSessionKey(videoId, userId, audioItag, null, videoItag, 0L))

    internal fun lookupByItag(videoId: String, userId: String, itag: Int): SabrSessionHolder? =
        registry.lookupByItag(videoId, userId, itag)

    internal fun lookupByToken(videoId: String, token: String, itag: Int): SabrSessionHolder? =
        registry.lookupByToken(videoId, token, itag)

    internal fun lookupByToken(videoId: String, token: String): SabrSessionHolder? =
        registry.lookupByToken(videoId, token)

    internal suspend fun ensureWarmed(holder: SabrSessionHolder, maxPumps: Int = 8) {
        pump.ensureWarmed(holder, maxPumps)
    }

    internal suspend fun fetchInfo(videoId: String, startTimeMs: Long = 0L): SabrPreparedInfo? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(SABR_INFO_TIMEOUT_MS) {
            val token = tokenClient.fetch(videoId) ?: return@withTimeoutOrNull null
            runCatching {
                YoutubeSabrProbe.fetchSabrInfo(
                    videoId,
                    YoutubeSabrClientProfile.WEB,
                    Localization("en", "GB"),
                    ContentCountry("GB"),
                    token.visitorBoundPoToken,
                    token.visitorData,
                    startTimeMs.toStartTimeSecs(),
                )
            }.getOrNull()?.let { SabrPreparedInfo(it, token) }
        }
    }

    internal suspend fun fetchSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
    ): SabrMediaSegment? = pump.fetchSegment(holder, request)

    internal suspend fun fetchMediaAt(holder: SabrSessionHolder, playerTimeMs: Long): List<SabrMediaSegment>? =
        pump.fetchMediaAt(holder, playerTimeMs)

    internal suspend fun fetchInitializationData(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): ByteArray? = SabrInitializationData.fetch(format, initCache)
        ?.also { holder.session.streamState.ingestInitializationData(format, it) }

    fun release() {
        idleCheckJob.cancel()
        scope.cancel()
        registry.clear()
    }

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

    private companion object {
        const val SABR_INFO_TIMEOUT_MS = 20_000L

        fun defaultMaxSessions(): Int =
            System.getenv("SABR_MAX_SESSIONS")?.toIntOrNull()?.coerceAtLeast(1) ?: 24

        fun defaultIdleEviction(): Duration = Duration.ofSeconds(
            System.getenv("SABR_IDLE_EVICTION_SECONDS")?.toLongOrNull()?.coerceAtLeast(60L) ?: 240L,
        )

        fun Long.toStartTimeSecs(): Int? {
            if (this <= 0L) return null
            return (this / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
