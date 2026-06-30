package dev.typetype.server.services

import dev.typetype.server.downloader.OkHttpDownloader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.io.IOException
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class SabrSessionStore(
    private val tokenServiceUrl: String,
    private val maxSessions: Int = 4,
    private val idleEviction: Duration = Duration.ofSeconds(90),
    private val pumpLoopIntervalMs: Long = 750,
) {
    private data class Key(
        val videoId: String,
        val userId: String,
        val audioItag: Int,
        val videoItag: Int,
    )

    internal class Holder(
        val session: YoutubeSabrSession,
        val info: YoutubeSabrInfo,
        val audioFormat: YoutubeSabrFormat,
        val videoFormat: YoutubeSabrFormat,
        val sessionToken: String,
        @Volatile var lastRequestAt: Instant,
        val pendingSignals: MutableSet<SabrSegmentRequest> = ConcurrentHashMap.newKeySet(),
        val pendingAwaits: ConcurrentHashMap<SabrSegmentRequest, CompletableDeferred<Unit>> = ConcurrentHashMap(),
        val pumpMutex: Mutex = Mutex(),
    )

    private val sessions = ConcurrentHashMap<Key, Holder>()
    private val sessionsByToken = ConcurrentHashMap<String, Holder>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val idleCheckJob: Job = scope.launch { idleEvictionLoop() }
    private val random = SecureRandom()

    init {
        runCatching { NewPipe.init(OkHttpDownloader.instance()) }
    }

    internal fun getOrCreate(
        videoId: String,
        userId: String,
        info: YoutubeSabrInfo,
        audioFormat: YoutubeSabrFormat,
        videoFormat: YoutubeSabrFormat,
    ): Holder {
        val key = Key(videoId, userId, audioFormat.itag, videoFormat.itag)
        sessions[key]?.let { it.lastRequestAt = Instant.now(); return it }
        ensureCapacityForNewSession()
        val provider = TypetypeTokenSabrPoTokenProvider(tokenServiceUrl)
        val session = YoutubeSabrSession(info, audioFormat, videoFormat, provider)
        val holder = Holder(session, info, audioFormat, videoFormat, newSessionToken(), Instant.now())
        sessions[key] = holder
        sessionsByToken[holder.sessionToken] = holder
        scope.launch { pumpLoop(key, holder) }
        return holder
    }

    internal fun lookup(videoId: String, userId: String, audioItag: Int, videoItag: Int): Holder? {
        val holder = sessions[Key(videoId, userId, audioItag, videoItag)]
        if (holder != null) holder.lastRequestAt = Instant.now()
        return holder
    }

    internal fun lookupByItag(videoId: String, userId: String, itag: Int): Holder? {
        val now = Instant.now()
        for ((key, holder) in sessions) {
            if (key.userId != userId) continue
            if (holder.info.videoId != videoId) continue
            if (holder.audioFormat.itag != itag && holder.videoFormat.itag != itag) continue
            holder.lastRequestAt = now
            return holder
        }
        return null
    }

    internal fun lookupByToken(videoId: String, token: String, itag: Int): Holder? {
        val holder = sessionsByToken[token] ?: return null
        if (holder.info.videoId != videoId) return null
        if (holder.audioFormat.itag != itag && holder.videoFormat.itag != itag) return null
        holder.lastRequestAt = Instant.now()
        return holder
    }

    internal suspend fun ensureWarmed(holder: Holder, maxPumps: Int = 8) {
        val localization = Localization("en", "GB")
        var pumps = 0
        while (pumps < maxPumps && !bothFormatsKnown(holder) && !holder.session.isComplete) {
            holder.pumpMutex.withLock {
                runCatching { holder.session.pumpOnce(localization) }
            }
            pumps++
        }
    }

    private fun bothFormatsKnown(holder: Holder): Boolean {
        val state = holder.session.streamState
        return state.getEndSegment(holder.audioFormat) > 0L &&
            state.getEndSegment(holder.videoFormat) > 0L &&
            state.getSegmentEndMs(holder.audioFormat, 1) > 0L &&
            state.getSegmentEndMs(holder.videoFormat, 1) > 0L
    }

    internal suspend fun fetchSegment(
        holder: Holder,
        request: SabrSegmentRequest,
    ): SabrMediaSegment? {
        holder.lastRequestAt = Instant.now()
        holder.session.getCachedSegment(request)?.let { return it }
        if (holder.session.isBeyondEnd(request)) return null
        val waiter = holder.pendingAwaits.computeIfAbsent(request) { CompletableDeferred() }
        holder.pendingSignals.add(request)
        return try {
            waiter.await()
            holder.session.getCachedSegment(request)
        } finally {
            holder.pendingAwaits.remove(request, waiter)
        }
    }

    fun release() {
        idleCheckJob.cancel()
        scope.cancel()
        sessions.clear()
        sessionsByToken.clear()
    }

    private suspend fun pumpLoop(key: Key, holder: Holder) {
        val localization = Localization("en", "GB")
        while (sessions.containsKey(key)) {
            try {
                holder.pumpMutex.withLock {
                    holder.pendingSignals.firstOrNull()?.let { signal ->
                        holder.session.prepareForRequestedSegment(signal)
                        holder.pendingSignals.remove(signal)
                    }
                    holder.session.evictPlayed()
                    holder.session.pumpOnce(localization)
                }
                holder.completeReadyWaiters()
                delay(pumpLoopIntervalMs)
            } catch (_: IOException) {
                delay(2_000)
            } catch (_: ExtractionException) {
                delay(2_000)
            } catch (_: Exception) {
                delay(2_000)
            }
        }
    }

    private suspend fun idleEvictionLoop() {
        while (true) {
            delay(15_000)
            val cutoff = Instant.now().minus(idleEviction)
            val stale = sessions.entries.filter { it.value.lastRequestAt.isBefore(cutoff) }.map { it.key }
            stale.forEach { removeSession(it) }
        }
    }

    private fun ensureCapacityForNewSession() {
        if (sessions.size < maxSessions) return
        val oldest = sessions.entries.minByOrNull { it.value.lastRequestAt } ?: return
        removeSession(oldest.key)
    }

    private fun removeSession(key: Key) {
        sessions.remove(key)?.let { holder ->
            sessionsByToken.remove(holder.sessionToken, holder)
            holder.pendingAwaits.values.forEach { it.complete(Unit) }
        }
    }

    private fun Holder.completeReadyWaiters() {
        for ((request, waiter) in pendingAwaits) {
            if (session.getCachedSegment(request) != null || session.isBeyondEnd(request)) {
                waiter.complete(Unit)
            }
        }
    }

    private fun YoutubeSabrSession.prepareForRequestedSegment(request: SabrSegmentRequest) {
        if (request.isInitializationSegment) return
        val state = streamState
        val max = state.getMaxSegment(request.format)
        when {
            request.sequenceNumber <= max -> prepareForRewind(request)
            request.sequenceNumber > max + 1 -> prepareForForwardJump(request)
            else -> prepareForMediaSegment(request)
        }
    }

    private fun newSessionToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
