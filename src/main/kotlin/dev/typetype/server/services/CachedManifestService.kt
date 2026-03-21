package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult

class CachedManifestService(
    private val delegate: ManifestService,
    private val cache: CacheService,
) {

    suspend fun dashManifest(videoUrl: String): ExtractionResult<String> {
        val key = "dash-manifest:${CachedStreamService.cacheKey(videoUrl)}"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return ExtractionResult.Success(cached)
        }
        val result = delegate.dashManifest(videoUrl)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, result.data, DASH_MANIFEST_TTL_SECONDS) }
        }
        return result
    }

    private companion object {
        const val DASH_MANIFEST_TTL_SECONDS = 21600L
    }
}
