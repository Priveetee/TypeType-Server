package dev.typetype.server.services

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

class SabrSessionStore(
    private val tokenServiceUrl: String,
    private val maxSessions: Int = defaultMaxSessions(),
    private val idleEviction: Duration = defaultIdleEviction(),
    private val pumpLoopIntervalMs: Long = 750,
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
    ): SabrSessionHolder {
        val key = SabrSessionKey(videoId, userId, audioFormat.itag, audioFormat.audioTrackId, videoFormat.itag)
        registry.get(key)?.let { return it }
        registry.ensureCapacity(maxSessions)
        val provider = TypetypeTokenSabrPoTokenProvider(tokenServiceUrl)
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
        registry.get(SabrSessionKey(videoId, userId, audioItag, null, videoItag))

    internal fun lookupByItag(videoId: String, userId: String, itag: Int): SabrSessionHolder? =
        registry.lookupByItag(videoId, userId, itag)

    internal fun lookupByToken(videoId: String, token: String, itag: Int): SabrSessionHolder? =
        registry.lookupByToken(videoId, token, itag)

    internal fun lookupByToken(videoId: String, token: String): SabrSessionHolder? =
        registry.lookupByToken(videoId, token)

    internal suspend fun ensureWarmed(holder: SabrSessionHolder, maxPumps: Int = 8) {
        pump.ensureWarmed(holder, maxPumps)
    }

    internal suspend fun fetchSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
    ): SabrMediaSegment? = pump.fetchSegment(holder, request)

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
        fun defaultMaxSessions(): Int =
            System.getenv("SABR_MAX_SESSIONS")?.toIntOrNull()?.coerceAtLeast(1) ?: 24

        fun defaultIdleEviction(): Duration = Duration.ofSeconds(
            System.getenv("SABR_IDLE_EVICTION_SECONDS")?.toLongOrNull()?.coerceAtLeast(60L) ?: 240L,
        )
    }
}
