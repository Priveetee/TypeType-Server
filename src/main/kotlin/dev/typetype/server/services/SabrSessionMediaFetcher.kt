package dev.typetype.server.services

import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import java.time.Instant

internal object SabrSessionMediaFetcher {
    suspend fun fetch(holder: SabrSessionHolder, playerTimeMs: Long): List<SabrMediaSegment>? {
        holder.lastRequestAt = Instant.now()
        val localization = Localization("en", "GB")
        return holder.pumpMutex.withLock {
            runCatchingNonCancellation {
                holder.session.fetchMediaAt(
                    playerTimeMs,
                    holder.isVideoActive(),
                    holder.isAudioActive(),
                    localization,
                )
            }.getOrNull()?.also { segments ->
                segments.forEach { holder.markServed(it) }
            }
        }
    }
}
