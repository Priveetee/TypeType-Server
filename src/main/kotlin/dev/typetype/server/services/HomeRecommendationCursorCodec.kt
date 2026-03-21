package dev.typetype.server.services

import java.util.Base64

object HomeRecommendationCursorCodec {
    fun decode(rawCursor: String?): HomeRecommendationCursor? {
        if (rawCursor.isNullOrBlank()) {
            return HomeRecommendationCursor(subscriptionIndex = 0, discoveryIndex = 0)
        }
        val decoded = runCatching {
            Base64.getUrlDecoder().decode(rawCursor).toString(Charsets.UTF_8)
        }.getOrNull() ?: return null
        val multi = Regex("""\{"s":(\d+),"d":(\d+)}""").matchEntire(decoded)
        if (multi != null) {
            val subscriptionIndex = multi.groupValues[1].toIntOrNull() ?: return null
            val discoveryIndex = multi.groupValues[2].toIntOrNull() ?: return null
            return HomeRecommendationCursor(
                subscriptionIndex = subscriptionIndex,
                discoveryIndex = discoveryIndex,
            )
        }
        val legacy = Regex("""\{"index":(\d+)}""").matchEntire(decoded)
        if (legacy != null) {
            val index = legacy.groupValues[1].toIntOrNull() ?: return null
            return HomeRecommendationCursor(subscriptionIndex = index, discoveryIndex = index)
        }
        return null
    }

    fun encode(cursor: HomeRecommendationCursor): String {
        val payload = """{"s":${cursor.subscriptionIndex},"d":${cursor.discoveryIndex}}"""
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }
}
