package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal object SabrDownloadInitialization {
    private val localization = Localization("en", "US")

    suspend fun fetch(
        store: SabrSessionStore,
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): ByteArray? {
        SabrInitializationData.fetch(holder.key.videoId, format, store.initCache)?.let {
            holder.session.streamState.ingestInitializationData(format, it)
            return it
        }
        val poToken = holder.session.streamState.poToken?.takeIf { it.isNotEmpty() }
            ?: holder.playerContextToken?.streamingPoTokenBytesFor(holder.info)
            ?: return store.fetchInitializationData(holder, format)
        val direct = runCatchingNonCancellation {
            runInterruptible(Dispatchers.IO) {
                holder.withPlayerContext {
                    fetchInitializationData(format, localization, DIRECT_TIMEOUT_MS, poToken)
                }
            }
        }.getOrNull() ?: return store.fetchInitializationData(holder, format)
        SabrInitializationData.remember(holder.key.videoId, format, direct, store.initCache)
        return direct
    }

    private const val DIRECT_TIMEOUT_MS = 5_000L
}
