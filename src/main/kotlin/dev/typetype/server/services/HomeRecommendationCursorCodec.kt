package dev.typetype.server.services

import java.util.Base64

object HomeRecommendationCursorCodec {
    fun decode(rawCursor: String?): HomeRecommendationCursor? {
        if (rawCursor.isNullOrBlank()) return HomeRecommendationCursor(0)
        val decoded = runCatching { Base64.getUrlDecoder().decode(rawCursor).toString(Charsets.UTF_8) }.getOrNull() ?: return null
        val match = Regex("""\{"index":(\d+)}""").matchEntire(decoded) ?: return null
        val index = match.groupValues[1].toIntOrNull() ?: return null
        return HomeRecommendationCursor(index = index)
    }

    fun encode(cursor: HomeRecommendationCursor): String {
        val payload = """{"index":${cursor.index}}"""
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }
}
