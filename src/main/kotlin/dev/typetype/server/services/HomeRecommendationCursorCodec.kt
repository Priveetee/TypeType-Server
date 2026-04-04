package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.Base64

object HomeRecommendationCursorCodec {
    private const val MULTI_PATTERN = """\{"s":(\d+),"d":(\d+)}"""
    private const val LEGACY_PATTERN = """\{"index":(\d+)}"""

    fun decode(rawCursor: String?): HomeRecommendationCursor? {
        if (rawCursor.isNullOrBlank()) {
            return HomeRecommendationCursor()
        }
        val decoded = runCatching {
            Base64.getUrlDecoder().decode(rawCursor).toString(Charsets.UTF_8)
        }.getOrNull() ?: return null
        val full = runCatching {
            CacheJson.decodeFromString<HomeRecommendationCursorPayload>(decoded)
        }.getOrNull()
        if (full != null) {
            return HomeRecommendationCursor(
                subscriptionIndex = full.s,
                discoveryIndex = full.d,
                subscriptionRun = full.r,
                preferDiscovery = full.p == 1,
                recentChannels = full.c,
            )
        }
        val multi = Regex(MULTI_PATTERN).matchEntire(decoded)
        if (multi != null) {
            val subscriptionIndex = multi.groupValues[1].toIntOrNull() ?: return null
            val discoveryIndex = multi.groupValues[2].toIntOrNull() ?: return null
            return HomeRecommendationCursor(
                subscriptionIndex = subscriptionIndex,
                discoveryIndex = discoveryIndex,
                subscriptionRun = 0,
                preferDiscovery = true,
            )
        }
        val legacy = Regex(LEGACY_PATTERN).matchEntire(decoded)
        if (legacy != null) {
            val index = legacy.groupValues[1].toIntOrNull() ?: return null
            return HomeRecommendationCursor(
                subscriptionIndex = index,
                discoveryIndex = index,
                subscriptionRun = 0,
                preferDiscovery = true,
            )
        }
        return null
    }

    fun encode(cursor: HomeRecommendationCursor): String {
        val payload = CacheJson.encodeToString(
            HomeRecommendationCursorPayload(
                s = cursor.subscriptionIndex,
                d = cursor.discoveryIndex,
                r = cursor.subscriptionRun,
                p = if (cursor.preferDiscovery) 1 else 0,
                c = cursor.recentChannels,
            )
        )
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }
}
