package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.HomeRecommendationPool
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

class HomeRecommendationPoolCache(private val cache: dev.typetype.server.cache.CacheService) {
    suspend fun read(key: String): HomeRecommendationPool? {
        val raw = runCatching { cache.get(key) }.getOrNull() ?: return null
        return runCatching { CacheJson.decodeFromString<HomeRecommendationPool>(raw) }.getOrNull()
    }

    suspend fun write(key: String, pool: HomeRecommendationPool) {
        runCatching { cache.set(key, CacheJson.encodeToString(pool), CACHE_TTL_SECONDS) }
    }

    fun key(userId: String, serviceId: Int, personalizationEnabled: Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val seed = "$CACHE_VERSION:$userId:$serviceId:$personalizationEnabled"
        val hex = digest.digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
        return "recommendations:home:$hex"
    }

    companion object {
        private const val CACHE_TTL_SECONDS = 300L
        private const val CACHE_VERSION = 7
    }
}
