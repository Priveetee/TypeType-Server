package dev.typetype.server.cache

interface CacheService {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String, ttlSeconds: Long)
    suspend fun delete(key: String)
}
