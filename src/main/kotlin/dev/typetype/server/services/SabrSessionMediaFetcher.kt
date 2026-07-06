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
            }.getOrNull()
                ?.filter { holder.shouldSend(it) }
                ?.takeIf { it.isNotEmpty() }
                ?: fetchActiveRequests(holder, playerTimeMs, localization)
        }
    }

    private fun fetchActiveRequests(
        holder: SabrSessionHolder,
        playerTimeMs: Long,
        localization: Localization,
    ): List<SabrMediaSegment>? {
        val requests = holder.mediaRequestsAt(playerTimeMs).filter { request ->
            request.isInitializationSegment || (holder.lastServedSequence(request.format) ?: 0) < request.sequenceNumber
        }
        val segments = requests.map { request ->
            holder.session.fetchTargetedSegment(holder, request, localization, playerTimeMs)
                ?: return null
        }
        return segments.takeIf { it.isNotEmpty() }
    }
}
