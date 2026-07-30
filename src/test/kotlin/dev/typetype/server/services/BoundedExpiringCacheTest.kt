package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class BoundedExpiringCacheTest {
    @Test
    fun `least recently used entry is removed at capacity`() {
        val cache = BoundedExpiringCache<String, String>(
            maxEntries = 2,
            ttl = Duration.ofMinutes(1),
        )
        cache.put("first", "1")
        cache.put("second", "2")
        cache.get("first")

        cache.put("third", "3")

        assertEquals("1", cache.get("first"))
        assertNull(cache.get("second"))
        assertEquals("3", cache.get("third"))
    }

    @Test
    fun `weight limit removes oldest entries`() {
        val cache = BoundedExpiringCache<String, String>(
            maxEntries = 10,
            maxWeight = 5,
            ttl = Duration.ofMinutes(1),
            weigher = { it.length.toLong() },
        )
        cache.put("first", "123")

        cache.put("second", "456")

        assertNull(cache.get("first"))
        assertEquals("456", cache.get("second"))
        assertEquals(3, cache.weight())
    }

    @Test
    fun `new writes purge expired entries without reading their keys`() {
        var now = 0L
        val cache = BoundedExpiringCache<String, String>(
            maxEntries = 10,
            ttl = Duration.ofMillis(10),
            clock = { now },
        )
        cache.put("expired", "old")
        now = 10L

        cache.put("current", "new")

        assertNull(cache.get("expired"))
        assertEquals(1, cache.size())
    }
}
