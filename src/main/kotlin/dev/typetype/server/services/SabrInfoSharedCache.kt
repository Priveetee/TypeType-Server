package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Base64

internal class SabrInfoSharedCache(private val cache: CacheService?) {
    suspend fun get(videoId: String): YoutubeSabrInfo? {
        val encoded = runCatching { cache?.get(cacheKey(videoId)) }.getOrNull() ?: return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(encoded)
            ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
                input.readObject() as? YoutubeSabrInfo
            }
        }.getOrNull()
    }

    suspend fun put(videoId: String, info: YoutubeSabrInfo): Unit {
        val target = cache ?: return
        val encoded = runCatching {
            val output = ByteArrayOutputStream()
            ObjectOutputStream(output).use { it.writeObject(info) }
            Base64.getEncoder().encodeToString(output.toByteArray())
        }.getOrNull() ?: return
        runCatching { target.set(cacheKey(videoId), encoded, TTL_SECONDS) }
    }

    private fun cacheKey(videoId: String): String = "sabr:info:v1:$videoId"

    private companion object {
        const val TTL_SECONDS = 600L
    }
}
