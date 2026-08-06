package dev.typetype.server

import dev.typetype.server.cache.CacheService
import java.util.concurrent.ConcurrentHashMap

class FakeCacheService : CacheService {
    private val values = ConcurrentHashMap<String, String>()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun set(key: String, value: String, ttlSeconds: Long) {
        values[key] = value
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }

    fun clear() {
        values.clear()
    }
}
