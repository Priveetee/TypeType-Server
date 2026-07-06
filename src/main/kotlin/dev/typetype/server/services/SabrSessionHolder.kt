package dev.typetype.server.services

import kotlinx.coroutines.sync.Mutex
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SabrSessionHolder(
    val session: YoutubeSabrSession,
    val info: YoutubeSabrInfo,
    val audioFormat: YoutubeSabrFormat,
    val videoFormat: YoutubeSabrFormat,
    val sessionToken: String,
    val key: SabrSessionKey,
    @Volatile var lastRequestAt: Instant,
    val pumpMutex: Mutex = Mutex(),
) {
    private val readerPositions = ConcurrentHashMap<Int, Long>()
    private val lastServedSequences = ConcurrentHashMap<Int, Int>()
    private val activeItags: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val pendingRefetch = AtomicReference<SabrSegmentRequest?>()
    private val pendingForwardSeek = AtomicReference<SabrSegmentRequest?>()
    private val pumpStarted = AtomicBoolean(false)
    private val activeWebSockets = AtomicInteger(0)
    private val segmentMemory = SabrMemorySegmentCache(MAX_SEGMENT_MEMORY_BYTES)
    @Volatile private var playerTimeMs: Long = 0L

    init {
        setActiveTracks(videoActive = true, audioActive = true)
    }

    fun setReaderPosition(format: YoutubeSabrFormat, endMs: Long): Unit {
        readerPositions[format.itag] = endMs
    }

    fun touch(now: Instant = Instant.now()): Unit {
        lastRequestAt = now
    }

    fun retainWebSocket(): Unit {
        activeWebSockets.incrementAndGet()
        touch()
    }

    fun releaseWebSocket(): Unit {
        if (activeWebSockets.decrementAndGet() < 0) activeWebSockets.set(0)
        touch()
    }

    fun hasActiveWebSocket(): Boolean = activeWebSockets.get() > 0

    fun markPumpStarted(): Boolean = pumpStarted.compareAndSet(false, true)

    fun setLastServedSequence(itag: Int, sequence: Int): Unit {
        lastServedSequences[itag] = sequence
    }

    fun cachedSegment(key: String): CachedSabrSegment? = segmentMemory.get(key)

    fun putCachedSegment(key: String, segment: CachedSabrSegment): Unit = segmentMemory.put(key, segment)

    fun evictCachedSegmentsBefore(ms: Long): Unit = segmentMemory.evictBefore(ms)

    fun lastServedSequence(format: YoutubeSabrFormat): Int? = lastServedSequences[format.itag]

    fun setPlayerTimeMs(ms: Long): Unit {
        playerTimeMs = ms.coerceAtLeast(0L)
    }

    fun playerTimeMs(): Long = playerTimeMs

    fun isAudioActive(): Boolean = activeItags.contains(audioFormat.itag)

    fun isVideoActive(): Boolean = activeItags.contains(videoFormat.itag)

    fun readerHeadMs(): Long = readerTailMs()

    fun readerPosition(format: YoutubeSabrFormat): Long? = readerPositions[format.itag]

    fun readerTailMs(): Long {
        if (activeItags.isEmpty()) return 0L
        var tail = Long.MAX_VALUE
        for (itag in activeItags) {
            val position = readerPositions[itag] ?: return 0L
            tail = minOf(tail, position)
        }
        return if (tail == Long.MAX_VALUE) 0L else tail
    }

    fun requestRefetch(request: SabrSegmentRequest): Unit {
        pendingRefetch.set(request)
    }

    fun requestForwardSeek(request: SabrSegmentRequest): Unit {
        pendingForwardSeek.set(request)
    }

    fun consumeRefetch(): SabrSegmentRequest? = pendingRefetch.getAndSet(null)

    fun consumeForwardSeek(): SabrSegmentRequest? = pendingForwardSeek.getAndSet(null)

    fun hasPendingSeek(): Boolean = pendingRefetch.get() != null || pendingForwardSeek.get() != null

    fun setActiveTracks(videoActive: Boolean, audioActive: Boolean): Unit {
        setActive(videoFormat.itag, videoActive)
        setActive(audioFormat.itag, audioActive)
        session.streamState.setActiveTrackTypes(videoActive, audioActive)
    }

    private fun setActive(itag: Int, active: Boolean): Unit {
        if (active) {
            activeItags.add(itag)
        } else {
            activeItags.remove(itag)
            readerPositions.remove(itag)
        }
    }

    private companion object {
        const val MAX_SEGMENT_MEMORY_BYTES = 64L * 1024L * 1024L
    }
}
