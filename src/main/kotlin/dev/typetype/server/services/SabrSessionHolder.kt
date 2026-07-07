package dev.typetype.server.services

import kotlinx.coroutines.sync.Mutex
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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
    private val readerPositions = ConcurrentHashMap<ReaderTrackKey, Long>()
    private val lastServedSequences = ConcurrentHashMap<ReaderTrackKey, Int>()
    private val activeItags: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val pendingRefetch = AtomicReference<SabrSegmentRequest?>()
    private val pendingForwardSeek = AtomicReference<SabrSegmentRequest?>()
    private val pumpStarted = AtomicBoolean(false)
    private val activeGeneration = AtomicLong(0L)
    private val segmentMemory = SabrMemorySegmentCache(MAX_SEGMENT_MEMORY_BYTES)
    private val playbackState = AtomicReference(SabrPlaybackState.IDLE)
    private val terminalError = AtomicReference<String?>()
    private val networkError = AtomicReference<String?>()
    @Volatile private var playerTimeMs: Long = 0L

    init {
        setActiveTracks(videoActive = true, audioActive = true)
    }

    fun activeGeneration(): Long = activeGeneration.get()

    fun advancePlaybackGeneration(ms: Long): Long {
        setPlayerTimeMs(ms)
        val generation = activeGeneration.incrementAndGet()
        clearReaderStateBefore(generation)
        return generation
    }

    fun setReaderPosition(format: YoutubeSabrFormat, endMs: Long): Unit =
        setReaderPosition(format, endMs, activeGeneration())

    fun setReaderPosition(format: YoutubeSabrFormat, endMs: Long, generation: Long): Unit {
        if (generation == activeGeneration()) readerPositions[ReaderTrackKey(generation, format.itag)] = endMs
    }

    fun touch(now: Instant = Instant.now()): Unit {
        lastRequestAt = now
    }

    fun markPumpStarted(): Boolean = pumpStarted.compareAndSet(false, true)

    fun setLastServedSequence(itag: Int, sequence: Int): Unit =
        setLastServedSequence(itag, sequence, activeGeneration())

    fun setLastServedSequence(itag: Int, sequence: Int, generation: Long): Unit {
        if (generation == activeGeneration()) lastServedSequences[ReaderTrackKey(generation, itag)] = sequence
    }

    fun cachedSegment(key: String): CachedSabrSegment? = segmentMemory.get(key)

    fun putCachedSegment(key: String, segment: CachedSabrSegment): Unit = segmentMemory.put(key, segment)

    fun evictCachedSegmentsBefore(ms: Long): Unit = segmentMemory.evictBefore(ms)

    fun lastServedSequence(format: YoutubeSabrFormat): Int? = lastServedSequence(format, activeGeneration())

    fun lastServedSequence(format: YoutubeSabrFormat, generation: Long): Int? =
        lastServedSequences[ReaderTrackKey(generation, format.itag)]

    fun setPlayerTimeMs(ms: Long): Unit {
        playerTimeMs = ms.coerceAtLeast(0L)
    }

    fun playerTimeMs(): Long = playerTimeMs

    fun isAudioActive(): Boolean = activeItags.contains(audioFormat.itag)

    fun isVideoActive(): Boolean = activeItags.contains(videoFormat.itag)

    fun readerHeadMs(): Long {
        var head = 0L
        val generation = activeGeneration()
        for (itag in activeItags) {
            head = maxOf(head, readerPositions[ReaderTrackKey(generation, itag)] ?: 0L)
        }
        return head
    }

    fun readerPosition(format: YoutubeSabrFormat): Long? = readerPosition(format, activeGeneration())

    fun readerPosition(format: YoutubeSabrFormat, generation: Long): Long? =
        readerPositions[ReaderTrackKey(generation, format.itag)]

    fun readerTailMs(): Long {
        if (activeItags.isEmpty()) return 0L
        var tail = Long.MAX_VALUE
        val generation = activeGeneration()
        for (itag in activeItags) {
            val position = readerPositions[ReaderTrackKey(generation, itag)] ?: return 0L
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

    fun setPlaybackState(state: SabrPlaybackState): Unit {
        playbackState.set(state)
    }

    fun playbackState(): SabrPlaybackState = playbackState.get()

    fun recordNetworkFailure(message: String?): Unit {
        networkError.set(message)
        playbackState.set(SabrPlaybackState.NETWORK_FAILED)
    }

    fun consumeNetworkFailure(): String? = networkError.getAndSet(null)

    fun failTerminal(message: String?): Unit {
        terminalError.set(message)
        playbackState.set(SabrPlaybackState.TERMINAL)
    }

    fun terminalFailure(): String? = terminalError.get()

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
            readerPositions.keys.removeIf { it.itag == itag }
            lastServedSequences.keys.removeIf { it.itag == itag }
        }
    }

    private fun clearReaderStateBefore(generation: Long): Unit {
        readerPositions.keys.removeIf { it.generation < generation }
        lastServedSequences.keys.removeIf { it.generation < generation }
    }

    private data class ReaderTrackKey(val generation: Long, val itag: Int)

    private companion object {
        const val MAX_SEGMENT_MEMORY_BYTES = 64L * 1024L * 1024L
    }
}
