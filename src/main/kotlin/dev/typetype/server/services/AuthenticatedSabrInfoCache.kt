package dev.typetype.server.services

import kotlinx.coroutines.CompletableDeferred
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

internal class AuthenticatedSabrInfoCache(
    ttl: Duration = Duration.ofMinutes(5),
    maxEntries: Int = 256,
) {
    private val items = BoundedExpiringCache<Key, SabrPreparedInfo>(
        maxEntries = maxEntries,
        ttl = ttl,
    )
    private val inFlight = ConcurrentHashMap<Key, CompletableDeferred<AuthenticatedSabrInfoResult>>()

    suspend fun getOrLoad(
        credentials: YoutubeSessionCredentials,
        videoId: String,
        loader: suspend () -> AuthenticatedSabrInfoResult,
    ): AuthenticatedSabrInfoResult {
        val key = Key(credentials.userId, credentials.fingerprint, videoId)
        items.get(key)?.let { return AuthenticatedSabrInfoResult.Ready(it) }
        val pending = CompletableDeferred<AuthenticatedSabrInfoResult>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return existing.await()
        return try {
            val result = loader()
            if (result is AuthenticatedSabrInfoResult.Ready) items.put(key, result.prepared)
            pending.complete(result)
            result
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    private data class Key(
        val userId: String,
        val credentialFingerprint: String,
        val videoId: String,
    )
}
