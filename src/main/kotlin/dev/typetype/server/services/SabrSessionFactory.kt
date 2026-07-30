package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.time.Instant

internal class SabrSessionFactory(
    private val tokenClient: TypetypeTokenSabrTokenClient,
) {
    fun create(
        key: SabrSessionKey,
        info: YoutubeSabrInfo,
        audioFormat: YoutubeSabrFormat,
        videoFormat: YoutubeSabrFormat,
        sessionToken: String,
        initialToken: SabrTokenBundle?,
        initialGeneration: Long,
    ): SabrSessionHolder {
        val provider = TypetypeTokenSabrPoTokenProvider(tokenClient, initialToken)
        val sessionInfo = if (key.sourceId == null) info else SabrSessionIdentity.fresh(info)
        val session = YoutubeSabrSession(sessionInfo, audioFormat, videoFormat, provider)
        session.streamState.setPlayerTimeMs(key.startTimeMs)
        runCatching { provider.getPoToken(sessionInfo, session.streamState) }
            .getOrNull()
            ?.let { session.streamState.setPoToken(it) }
        return SabrSessionHolder(
            session,
            sessionInfo,
            audioFormat,
            videoFormat,
            sessionToken,
            key,
            Instant.now(),
            initialToken,
            initialGeneration = initialGeneration,
        ).also { it.setPlayerTimeMs(key.startTimeMs) }
    }
}
