package dev.typetype.server.services

import dev.typetype.server.models.YoutubeSessionCompleteRequest
import dev.typetype.server.models.YoutubeSessionPairingResponse
import dev.typetype.server.models.YoutubeSessionStatusResponse

class YoutubeSessionService(
    private val crypto: YoutubeSessionCrypto?,
    private val pairingStore: YoutubeSessionPairingStore = YoutubeSessionPairingStore(),
    private val store: YoutubeSessionStore = YoutubeSessionStore(),
) {
    val isConfigured: Boolean = crypto != null

    suspend fun createPairing(userId: String): YoutubeSessionPairingResponse =
        pairingStore.create(userId)

    suspend fun complete(request: YoutubeSessionCompleteRequest): YoutubeSessionCompleteResult {
        val crypto = crypto ?: return YoutubeSessionCompleteResult.Unavailable
        val code = request.code.trim().uppercase()
        val cookies = YoutubeSessionCookieNormalizer.normalize(request.cookies)
            ?: return YoutubeSessionCompleteResult.InvalidCredentials
        val poToken = request.poToken.trim()
        if (code.isBlank() || !YoutubeSessionCredentialValidator.isValid(cookies, poToken)) {
            return YoutubeSessionCompleteResult.InvalidCredentials
        }
        return store.complete(
            code = code,
            encryptedCookies = crypto.encrypt(cookies),
            encryptedPoToken = crypto.encrypt(poToken),
        )
    }

    suspend fun completeRemote(userId: String, rawCookies: String, rawPoToken: String): YoutubeSessionCompleteResult {
        val crypto = crypto ?: return YoutubeSessionCompleteResult.Unavailable
        val cookies = YoutubeSessionCookieNormalizer.normalize(rawCookies)
            ?: return YoutubeSessionCompleteResult.InvalidCredentials
        val poToken = rawPoToken.trim()
        if (!YoutubeSessionCredentialValidator.isValid(cookies, poToken)) {
            return YoutubeSessionCompleteResult.InvalidCredentials
        }
        store.completeForUser(
            userId = userId,
            encryptedCookies = crypto.encrypt(cookies),
            encryptedPoToken = crypto.encrypt(poToken),
        )
        return YoutubeSessionCompleteResult.Completed
    }

    suspend fun status(userId: String): YoutubeSessionStatusResponse =
        if (isConfigured) store.status(userId) else YoutubeSessionStatusResponse(YoutubeSessionStatus.Disconnected.value, 0, 0)

    suspend fun delete(userId: String): Boolean = store.delete(userId)

    suspend fun connectedCredentials(userId: String): YoutubeSessionCredentials? {
        val crypto = crypto ?: return null
        val encrypted = store.connectedEncrypted(userId) ?: return null
        val credentials = runCatching {
            YoutubeSessionCredentials(
                userId = userId,
                fingerprint = PublicCacheKey.of("youtube-session", encrypted.first, encrypted.second),
                cookies = crypto.decrypt(encrypted.first),
                poToken = crypto.decrypt(encrypted.second),
            )
        }.getOrNull()
        if (credentials == null) store.markNeedsReconnect(userId)
        return credentials
    }

    suspend fun markUsed(userId: String): Unit = store.markUsed(userId)

    suspend fun markNeedsReconnect(userId: String): Unit = store.markNeedsReconnect(userId)
}
