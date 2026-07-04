package dev.typetype.server.services

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal class SabrPreparedInfoCache(
    private val ttl: Duration = Duration.ofMinutes(10),
) {
    private val items = ConcurrentHashMap<String, Entry>()

    fun get(videoId: String): SabrPreparedInfo? {
        val entry = items[videoId] ?: return null
        if (entry.createdAt.plus(ttl).isBefore(Instant.now())) {
            items.remove(videoId, entry)
            return null
        }
        return entry.value
    }

    fun put(videoId: String, value: SabrPreparedInfo): SabrPreparedInfo {
        items[videoId] = Entry(value, Instant.now())
        return value
    }

    private data class Entry(val value: SabrPreparedInfo, val createdAt: Instant)
}
