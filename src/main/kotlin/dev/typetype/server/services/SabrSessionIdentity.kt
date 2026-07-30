package dev.typetype.server.services

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.sabr.TypeTypeYoutubeSabrInfoFactory
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

internal object SabrSessionIdentity {
    fun fresh(info: YoutubeSabrInfo): YoutubeSabrInfo {
        val cpn = YoutubeParsingHelper.generateContentPlaybackNonce()
        val url = requireNotNull(info.serverAbrStreamingUrl)
            .toHttpUrl()
            .newBuilder()
            .setQueryParameter("cpn", cpn)
            .build()
            .toString()
        return TypeTypeYoutubeSabrInfoFactory.withPlaybackIdentity(
            info,
            url,
            info.clientVersion,
            cpn,
            info.visitorData,
        )
    }
}
