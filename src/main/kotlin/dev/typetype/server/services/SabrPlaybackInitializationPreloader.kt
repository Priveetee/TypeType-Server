package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

internal object SabrPlaybackInitializationPreloader {
    suspend fun preload(
        sessionStore: SabrSessionStore,
        holder: SabrSessionHolder,
        timeoutMs: Long,
    ): Pair<ByteArray?, ByteArray?> = withTimeoutOrNull(timeoutMs) {
        coroutineScope {
            val video = async(Dispatchers.IO) { sessionStore.fetchInitializationData(holder, holder.videoFormat) }
            val audio = async(Dispatchers.IO) { sessionStore.fetchInitializationData(holder, holder.audioFormat) }
            video.await() to audio.await()
        }
    } ?: (null to null)
}
