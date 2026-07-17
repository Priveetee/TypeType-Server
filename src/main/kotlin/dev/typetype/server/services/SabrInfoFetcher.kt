package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe
import org.slf4j.LoggerFactory

internal class SabrInfoFetcher(
    private val tokenClient: TypetypeTokenSabrTokenClient,
    private val sessionClient: TypetypeTokenYoutubeSessionClient? = null,
    private val infoCache: SabrPreparedInfoCache = SabrPreparedInfoCache(),
    sharedCache: CacheService? = null,
) {
    private val repository = SabrInfoRepository(infoCache, sharedCache)

    suspend fun fetchInfo(
        videoId: String,
        startTimeMs: Long = 0L,
        cachedFirst: Boolean = false,
    ): SabrPreparedInfo? = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        if (cachedFirst) repository.local(videoId, startTimeMs)?.let {
            logFetch(videoId, startTimeMs, startedAt, "cache")
            return@withContext it
        }
        if (cachedFirst) tokenClient.fetch(videoId)?.let { token ->
            repository.shared(videoId, token)?.let { prepared ->
                logFetch(videoId, startTimeMs, startedAt, "shared_cache")
                return@withContext prepared
            }
        }
        fetchPlayableWithRetries(videoId, startTimeMs)?.let {
            logFetch(videoId, startTimeMs, startedAt, "network")
            return@withContext it
        }
        if (startTimeMs > 0L) fetchPlayableWithRetries(videoId, 0L)?.let {
            logFetch(videoId, startTimeMs, startedAt, "network_start0")
            return@withContext it
        }
        repository.local(videoId, startTimeMs)?.also { logFetch(videoId, startTimeMs, startedAt, "late_cache") }
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

    suspend fun rememberExtractedInfo(videoId: String, info: YoutubeSabrInfo): Unit {
        repository.rememberInitialization(videoId, info)
        fetchInfoOnce(videoId, startTimeMs = 0L, forceRefresh = false)
            ?.takeIf { it.hasAudioAndVideoFormats() }
            ?.let { repository.putPrepared(videoId, startTimeMs = 0L, it) }
    }

    suspend fun invalidatePlayback(videoId: String): Unit = repository.invalidatePlayback(videoId)

    fun initializationFormat(videoId: String, target: YoutubeSabrFormat): YoutubeSabrFormat? =
        repository.initializationFormat(videoId, target)

    private suspend fun fetchPlayableWithRetries(videoId: String, startTimeMs: Long): SabrPreparedInfo? {
        repeat(SabrSessionStoreDefaults.INFO_ATTEMPTS) { attempt ->
            fetchInfoOnce(videoId, startTimeMs, forceRefresh = attempt > 0)
                ?.let { return repository.putPrepared(videoId, startTimeMs, it) }
            if (attempt + 1 < SabrSessionStoreDefaults.INFO_ATTEMPTS) delay(SabrSessionStoreDefaults.INFO_RETRY_DELAY_MS)
        }
        return null
    }

    private suspend fun fetchInfoOnce(videoId: String, startTimeMs: Long, forceRefresh: Boolean): SabrPreparedInfo? =
        withTimeoutOrNull(SabrSessionStoreDefaults.INFO_TIMEOUT_MS) {
            val refreshedToken = if (forceRefresh) tokenClient.fetch(videoId, forceRefresh = true) else null
            val tokenSession = sessionClient?.fetchPlaybackSession(videoId)
            tokenSession?.token
                ?.takeIf { it.visitorData == tokenSession.info.visitorData }
                ?.let { sessionToken ->
                    SabrPreparedInfo(
                        tokenSession.info,
                        sessionToken,
                        isLive = tokenSession.isLive,
                        isLiveContent = tokenSession.isLiveContent,
                    )
                        .takeIf { it.hasAudioAndVideoFormats() }
                        ?.let { return@withTimeoutOrNull it }
                }
            val token = refreshedToken ?: tokenClient.fetch(videoId)
                ?: return@withTimeoutOrNull null.also {
                    logger.warn(
                        "sabr_probe event=token_missing videoId={} startTimeMs={} forceRefresh={}",
                        videoId,
                        startTimeMs,
                        forceRefresh,
                    )
                }
            tokenSession
                ?.takeIf { it.info.visitorData == token.visitorData }
                ?.let {
                    SabrPreparedInfo(
                        it.info,
                        token,
                        isLive = it.isLive,
                        isLiveContent = it.isLiveContent,
                    )
                }
                ?.takeIf { it.hasAudioAndVideoFormats() }
                ?.let { return@withTimeoutOrNull it }
            CLIENT_PROFILES.firstNotNullOfOrNull { profile ->
                fetchInfoForProfileWithTokenFallback(videoId, startTimeMs, token, profile)
                    ?.takeIf { it.hasAudioAndVideoFormats() }
            }
        }

    private fun fetchInfoForProfileWithTokenFallback(
        videoId: String,
        startTimeMs: Long,
        token: SabrTokenBundle,
        profile: YoutubeSabrClientProfile,
    ): SabrPreparedInfo? {
        fetchInfoForProfile(
            videoId = videoId,
            startTimeMs = startTimeMs,
            token = token,
            profile = profile,
            poToken = token.visitorBoundPoToken,
            tokenKind = VISITOR_BOUND_TOKEN_KIND,
        )?.let { return it }
        if (token.visitorBoundPoToken == token.videoBoundPoToken) return null
        return fetchInfoForProfile(
            videoId = videoId,
            startTimeMs = startTimeMs,
            token = token,
            profile = profile,
            poToken = token.videoBoundPoToken,
            tokenKind = VIDEO_BOUND_TOKEN_KIND,
        )
    }

    private fun fetchInfoForProfile(
        videoId: String,
        startTimeMs: Long,
        token: SabrTokenBundle,
        profile: YoutubeSabrClientProfile,
        poToken: String,
        tokenKind: String,
    ): SabrPreparedInfo? {
        val result = runCatching {
            YoutubeSabrProbe.fetchSabrInfo(
                videoId,
                profile,
                LOCALIZATION,
                CONTENT_COUNTRY,
                poToken,
                token.visitorData,
            )
        }
        result.onFailure { error ->
            logger.warn(
                "sabr_probe event=fetch_failed videoId={} profile={} tokenKind={} startTimeMs={} errorType={} error={}",
                videoId,
                profile,
                tokenKind,
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
                    "sabr_probe event=no_av_formats videoId={} profile={} tokenKind={} formatCount={} hasAudio={} hasVideo={} streamingUrl={}",
                    videoId,
                    profile,
                    tokenKind,
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
        val LOCALIZATION = Localization("en", "US")
        val CONTENT_COUNTRY = ContentCountry("US")
        val CLIENT_PROFILES = listOf(YoutubeSabrClientProfile.WEB, YoutubeSabrClientProfile.MWEB)
        const val VIDEO_BOUND_TOKEN_KIND = "video_bound"
        const val VISITOR_BOUND_TOKEN_KIND = "visitor_bound"
    }
}
