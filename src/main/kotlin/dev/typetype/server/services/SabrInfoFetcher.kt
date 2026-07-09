package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe
import org.slf4j.LoggerFactory

internal class SabrInfoFetcher(
    private val tokenClient: TypetypeTokenSabrTokenClient,
    private val infoCache: SabrPreparedInfoCache = SabrPreparedInfoCache(),
) {
    suspend fun fetchInfo(
        videoId: String,
        startTimeMs: Long = 0L,
        cachedFirst: Boolean = false,
    ): SabrPreparedInfo? = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        if (cachedFirst) playableCachedInfo(videoId, startTimeMs)?.let {
            logFetch(videoId, startTimeMs, startedAt, "cache")
            return@withContext it
        }
        fetchPlayableWithRetries(videoId, startTimeMs)?.let {
            logFetch(videoId, startTimeMs, startedAt, "network")
            return@withContext it
        }
        if (startTimeMs > 0L) fetchPlayableWithRetries(videoId, 0L)?.let {
            logFetch(videoId, startTimeMs, startedAt, "network_start0")
            return@withContext it
        }
        playableCachedInfo(videoId, startTimeMs)?.also { logFetch(videoId, startTimeMs, startedAt, "late_cache") }
    }

    private fun logFetch(videoId: String, startTimeMs: Long, startedAt: Long, source: String): Unit {
        logger.info(
            "sabr_info_fetch videoId={} startTimeMs={} source={} elapsedMs={}",
            videoId,
            startTimeMs,
            source,
            System.currentTimeMillis() - startedAt,
        )
    }

    fun rememberExtractedInfo(videoId: String, info: YoutubeSabrInfo): Unit {
        if (!SabrPreparedInfo(info, null).hasAudioAndVideoFormats()) return
        infoCache.put(videoId, startTimeMs = 0L, SabrPreparedInfo(info, null))
    }

    private suspend fun fetchPlayableWithRetries(videoId: String, startTimeMs: Long): SabrPreparedInfo? {
        repeat(SabrSessionStoreDefaults.INFO_ATTEMPTS) { attempt ->
            fetchInfoOnce(videoId, startTimeMs, forceRefresh = attempt > 0)
                ?.let { return infoCache.put(videoId, startTimeMs, it) }
            if (attempt + 1 < SabrSessionStoreDefaults.INFO_ATTEMPTS) delay(SabrSessionStoreDefaults.INFO_RETRY_DELAY_MS)
        }
        return null
    }

    private fun playableCachedInfo(videoId: String, startTimeMs: Long): SabrPreparedInfo? {
        val cached = infoCache.get(videoId, startTimeMs) ?: return null
        if (cached.hasAudioAndVideoFormats()) return cached
        infoCache.remove(videoId, startTimeMs)
        return null
    }

    private suspend fun fetchInfoOnce(videoId: String, startTimeMs: Long, forceRefresh: Boolean): SabrPreparedInfo? =
        withTimeoutOrNull(SabrSessionStoreDefaults.INFO_TIMEOUT_MS) {
            val token = tokenClient.fetch(videoId, forceRefresh = forceRefresh)
                ?: return@withTimeoutOrNull null.also {
                    logger.warn(
                        "sabr_probe event=token_missing videoId={} startTimeMs={} forceRefresh={}",
                        videoId,
                        startTimeMs,
                        forceRefresh,
                    )
                }
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
    ): SabrPreparedInfo? {
        val result = runCatching {
            YoutubeSabrProbe.fetchSabrInfo(
                videoId,
                profile,
                LOCALIZATION,
                CONTENT_COUNTRY,
                token.visitorBoundPoToken,
                token.visitorData,
            )
        }
        result.onFailure { error ->
            logger.warn(
                "sabr_probe event=fetch_failed videoId={} profile={} startTimeMs={} errorType={} error={}",
                videoId,
                profile,
                startTimeMs,
                error.javaClass.simpleName,
                error.message,
                error,
            )
        }
        return result.getOrNull()?.let { info ->
            val prepared = SabrPreparedInfo(info, token)
            if (!prepared.hasAudioAndVideoFormats()) {
                logger.warn(
                    "sabr_probe event=no_av_formats videoId={} profile={} formatCount={} hasAudio={} hasVideo={} streamingUrl={}",
                    videoId,
                    profile,
                    info.formats.size,
                    info.formats.any { it.isAudio },
                    info.formats.any { it.isVideo },
                    !info.serverAbrStreamingUrl.isNullOrEmpty(),
                )
            }
            prepared
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SabrInfoFetcher::class.java)
        val LOCALIZATION = Localization("en", "GB")
        val CONTENT_COUNTRY = ContentCountry("GB")
        val CLIENT_PROFILES = listOf(YoutubeSabrClientProfile.MWEB, YoutubeSabrClientProfile.WEB)
    }
}
