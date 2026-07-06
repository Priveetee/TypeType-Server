package dev.typetype.server.services

import dev.typetype.server.services.SabrSessionStoreDefaults.toStartTimeSecs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe

internal class SabrInfoFetcher(
    private val tokenClient: TypetypeTokenSabrTokenClient,
    private val infoCache: SabrPreparedInfoCache = SabrPreparedInfoCache(),
) {
    suspend fun fetchInfo(
        videoId: String,
        startTimeMs: Long = 0L,
        cachedFirst: Boolean = false,
    ): SabrPreparedInfo? = withContext(Dispatchers.IO) {
        if (cachedFirst) playableCachedInfo(videoId)?.let { return@withContext it }
        fetchPlayableWithRetries(videoId, startTimeMs)?.let { return@withContext it }
        if (startTimeMs > 0L) fetchPlayableWithRetries(videoId, 0L)?.let { return@withContext it }
        playableCachedInfo(videoId)
    }

    private suspend fun fetchPlayableWithRetries(videoId: String, startTimeMs: Long): SabrPreparedInfo? {
        repeat(SabrSessionStoreDefaults.INFO_ATTEMPTS) { attempt ->
            fetchInfoOnce(videoId, startTimeMs, forceRefresh = attempt > 0)
                ?.let { return infoCache.put(videoId, it) }
            if (attempt + 1 < SabrSessionStoreDefaults.INFO_ATTEMPTS) delay(SabrSessionStoreDefaults.INFO_RETRY_DELAY_MS)
        }
        return null
    }

    private fun playableCachedInfo(videoId: String): SabrPreparedInfo? {
        val cached = infoCache.get(videoId) ?: return null
        if (cached.hasAudioAndVideoFormats()) return cached
        infoCache.remove(videoId)
        return null
    }

    private suspend fun fetchInfoOnce(videoId: String, startTimeMs: Long, forceRefresh: Boolean): SabrPreparedInfo? =
        withTimeoutOrNull(SabrSessionStoreDefaults.INFO_TIMEOUT_MS) {
            val token = tokenClient.fetch(videoId, forceRefresh = forceRefresh) ?: return@withTimeoutOrNull null
            CLIENT_PROFILES.firstNotNullOfOrNull { profile ->
                fetchInfoForProfile(videoId, startTimeMs, token, profile)
                    ?.takeIf { it.hasAudioAndVideoFormats() }
            }
        }

    private fun fetchInfoForProfile(
        videoId: String,
        startTimeMs: Long,
        token: SabrTokenBundle,
        profile: YoutubeSabrClientProfile,
    ): SabrPreparedInfo? = runCatching {
        YoutubeSabrProbe.fetchSabrInfo(
            videoId,
            profile,
            LOCALIZATION,
            CONTENT_COUNTRY,
            token.visitorBoundPoToken,
            token.visitorData,
            startTimeMs.toStartTimeSecs(),
        )
    }.getOrNull()?.let { SabrPreparedInfo(it, token) }

    private companion object {
        val LOCALIZATION = Localization("en", "GB")
        val CONTENT_COUNTRY = ContentCountry("GB")
        val CLIENT_PROFILES = listOf(YoutubeSabrClientProfile.WEB, YoutubeSabrClientProfile.MWEB)
    }
}
