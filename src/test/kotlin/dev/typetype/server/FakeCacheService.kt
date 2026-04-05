package dev.typetype.server

import dev.typetype.server.cache.CacheService

class FakeCacheService : CacheService {
    private val values = mutableMapOf<String, String>()

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
