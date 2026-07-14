package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrPoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRecoverableException
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

internal class TypetypeTokenSabrPoTokenProvider(
    private val tokenClient: TypetypeTokenSabrTokenClient,
    private val initialToken: SabrTokenBundle? = null,
) : SabrPoTokenProvider {
    constructor(tokenServiceUrl: String) : this(TypetypeTokenSabrTokenClient(tokenServiceUrl))

    override fun getPoToken(info: YoutubeSabrInfo, streamState: YoutubeSabrStreamState): ByteArray? =
        fetch(info, forceRefresh = false)

    override fun getPoToken(
        info: YoutubeSabrInfo,
        streamState: YoutubeSabrStreamState,
        forceRefresh: Boolean,
    ): ByteArray? = fetch(info, forceRefresh)

    private fun fetch(info: YoutubeSabrInfo, forceRefresh: Boolean): ByteArray? {
        val token = if (!forceRefresh && initialToken?.videoId == info.videoId) initialToken else {
            tokenClient.fetch(info.videoId, refreshVideo = forceRefresh)
        }
        return token?.streamingPoTokenBytesFor(info)
            ?: token?.let { throw SabrRecoverableException(SABR_TOKEN_BINDING_FAILURE) }
    }
}
