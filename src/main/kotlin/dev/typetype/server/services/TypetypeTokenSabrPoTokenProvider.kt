package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrPoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

internal class TypetypeTokenSabrPoTokenProvider(
    private val tokenClient: TypetypeTokenSabrTokenClient,
    private val initialToken: SabrTokenBundle? = null,
) : SabrPoTokenProvider {
    constructor(tokenServiceUrl: String) : this(TypetypeTokenSabrTokenClient(tokenServiceUrl))

    override fun getPoToken(info: YoutubeSabrInfo, streamState: YoutubeSabrStreamState): ByteArray? =
        fetch(info.videoId, forceRefresh = false)

    override fun getPoToken(
        info: YoutubeSabrInfo,
        streamState: YoutubeSabrStreamState,
        forceRefresh: Boolean,
    ): ByteArray? = fetch(info.videoId, forceRefresh)

    private fun fetch(videoId: String, forceRefresh: Boolean): ByteArray? {
        if (!forceRefresh && initialToken?.videoId == videoId) return initialToken.streamingPoTokenBytes
        return tokenClient.fetch(videoId, forceRefresh = forceRefresh)?.streamingPoTokenBytes
    }
}
