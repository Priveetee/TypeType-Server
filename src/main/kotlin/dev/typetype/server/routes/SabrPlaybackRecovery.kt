package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

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
        if (failure.contains("SABR demand stalled")) {
            sessionStore.invalidatePlaybackInfo(holder.key.videoId)
            return RETRY_FRESH_SESSION
        }
        return null
    }

    fun retryVideoItags(holder: SabrSessionHolder): List<Int> {
        if (!holder.terminalFailure().orEmpty().contains("protected no-media")) return emptyList()
        val current = holder.videoFormat.takeIf { it.hasKnownVideoQuality() } ?: return emptyList()
        return holder.info.formats.asSequence()
            .filter { it.isVideo && it.itag != current.itag && it.hasKnownVideoQuality() }
            .filter { VIDEO_QUALITY_COMPARATOR.compare(it, current) < 0 }
            .sortedWith(VIDEO_QUALITY_COMPARATOR.reversed())
            .map { it.itag }
            .distinct()
            .take(MAX_RETRY_VIDEO_ITAGS)
            .toList()
    }

    private fun YoutubeSabrFormat.hasKnownVideoQuality(): Boolean =
        height > 0 && width > 0 && bitrate > 0

    private companion object {
        const val RETRY_FRESH_SESSION = "retry_fresh_session"
        const val RETRY_FRESH_SESSION_LOWER_VIDEO_ITAG = "retry_fresh_session_lower_video_itag"
        const val MAX_RETRY_VIDEO_ITAGS = 5
        val VIDEO_QUALITY_COMPARATOR: Comparator<YoutubeSabrFormat> =
            compareBy<YoutubeSabrFormat> { it.height }
                .thenBy { it.width }
                .thenBy { it.bitrate }
    }
}
