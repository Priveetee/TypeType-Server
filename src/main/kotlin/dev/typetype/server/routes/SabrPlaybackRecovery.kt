package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore

internal class SabrPlaybackRecovery(private val sessionStore: SabrSessionStore) {
    suspend fun action(holder: SabrSessionHolder): String? {
        val failure = holder.terminalFailure() ?: return null
        if (failure.contains("Expected UMP response", ignoreCase = true)) {
            sessionStore.invalidatePlaybackInfo(holder.key.videoId)
            return RETRY_FRESH_SESSION
        }
        if (failure.contains("protected no-media")) {
            return RETRY_FRESH_SESSION_LOWER_VIDEO_ITAG
        }
        return RETRY_FRESH_SESSION.takeIf { failure.contains("SABR demand stalled") }
    }

    fun retryVideoItags(holder: SabrSessionHolder): List<Int> {
        if (!holder.terminalFailure().orEmpty().contains("protected no-media")) return emptyList()
        return holder.info.formats.asSequence()
            .filter { it.isVideo && it.itag != holder.videoFormat.itag }
            .sortedByDescending { it.bitrate }
            .map { it.itag }
            .distinct()
            .take(MAX_RETRY_VIDEO_ITAGS)
            .toList()
    }

    private companion object {
        const val RETRY_FRESH_SESSION = "retry_fresh_session"
        const val RETRY_FRESH_SESSION_LOWER_VIDEO_ITAG = "retry_fresh_session_lower_video_itag"
        const val MAX_RETRY_VIDEO_ITAGS = 5
    }
}
