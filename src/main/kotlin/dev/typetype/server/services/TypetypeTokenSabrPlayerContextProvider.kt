package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRecoverableException
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrPlayerContext
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrPlayerContextProvider

internal class TypetypeTokenSabrPlayerContextProvider(
    private val tokenClient: TypetypeTokenSabrTokenClient,
    initialToken: SabrTokenBundle? = null,
) : YoutubeSabrPlayerContextProvider {
    @Volatile
    private var currentToken: SabrTokenBundle? = initialToken

    override fun getPlayerContext(
        videoId: String,
        profile: YoutubeSabrClientProfile,
    ): YoutubeSabrPlayerContext = getPlayerContext(videoId, profile, false)

    override fun getPlayerContext(
        videoId: String,
        profile: YoutubeSabrClientProfile,
        forceRefresh: Boolean,
    ): YoutubeSabrPlayerContext {
        val token = currentToken
            ?.takeIf { !forceRefresh && it.videoId == videoId }
            ?: tokenClient.fetch(videoId, forceRefresh = forceRefresh)
            ?: throw SabrRecoverableException(PLAYER_CONTEXT_UNAVAILABLE)
        currentToken = token
        return YoutubeSabrPlayerContext(token.visitorData, token.visitorBoundPoToken)
    }

    fun tokenFor(info: YoutubeSabrInfo): SabrTokenBundle? = currentToken
        ?.takeIf { it.videoId == info.videoId && it.visitorData == info.visitorData }

    private companion object {
        const val PLAYER_CONTEXT_UNAVAILABLE = "YouTube player admission context is unavailable"
    }
}
