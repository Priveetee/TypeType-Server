package dev.typetype.server.services

import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.slf4j.LoggerFactory
import java.time.Instant

internal object SabrSessionMediaFetcher {
    private val logger = LoggerFactory.getLogger(SabrSessionMediaFetcher::class.java)

    suspend fun fetch(holder: SabrSessionHolder, playerTimeMs: Long): List<SabrMediaSegment>? {
        holder.lastRequestAt = Instant.now()
        val localization = Localization("en", "GB")
        return holder.pumpMutex.withLock {
            val requests = holder.mediaRequestsAt(playerTimeMs)
            if (requests.isEmpty()) return@withLock emptyList()
            val needsVideo = requests.any { it.format.itag == holder.videoFormat.itag }
            val needsAudio = requests.any { it.format.itag == holder.audioFormat.itag }
            val result = runCatchingNonCancellation {
                holder.session.fetchMediaAt(
                    playerTimeMs,
                    needsVideo,
                    needsAudio,
                    localization,
                )
            }.onFailure { error ->
                logger.warn(
                    "sabr_media_fetch_failed videoId={} playerTimeMs={} video={} audio={} error={}",
                    holder.key.videoId,
                    playerTimeMs,
                    needsVideo,
                    needsAudio,
                    error.message,
                    error,
                )
            }
            result.getOrNull()
                ?.filter { holder.shouldSend(it) }
                ?.takeIf { it.isNotEmpty() }
        }
    }
}
