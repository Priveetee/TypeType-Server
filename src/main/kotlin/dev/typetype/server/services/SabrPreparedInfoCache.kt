package dev.typetype.server.services

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal class SabrPreparedInfoCache(
    private val ttl: Duration = Duration.ofMinutes(10),
) {
    private val items = ConcurrentHashMap<Key, Entry>()

    fun get(videoId: String, startTimeMs: Long): SabrPreparedInfo? {
        val key = Key(videoId, startBucket(startTimeMs))
        val entry = items[key] ?: return null
        if (entry.createdAt.plus(ttl).isBefore(Instant.now())) {
            items.remove(key, entry)
            return null
        }
        return entry.value
    }

    fun remove(videoId: String, startTimeMs: Long): Unit {
        items.remove(Key(videoId, startBucket(startTimeMs)))
    }

    fun remove(videoId: String): Unit {
        items.keys.removeIf { it.videoId == videoId }
    }

    fun put(videoId: String, startTimeMs: Long, value: SabrPreparedInfo): SabrPreparedInfo {
        items[Key(videoId, startBucket(startTimeMs))] = Entry(value, Instant.now())
        return value
    }

    private fun startBucket(startTimeMs: Long): Long = startTimeMs.coerceAtLeast(0L) / START_BUCKET_MS

    private data class Key(val videoId: String, val startBucket: Long)

    private data class Entry(val value: SabrPreparedInfo, val createdAt: Instant)

    private companion object {
        const val START_BUCKET_MS = 30_000L
    }
}
