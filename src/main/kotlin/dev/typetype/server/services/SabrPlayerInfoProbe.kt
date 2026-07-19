package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe

internal fun interface SabrPlayerInfoProbe {
    fun fetch(
        videoId: String,
        profile: YoutubeSabrClientProfile,
        token: SabrTokenBundle,
    ): YoutubeSabrInfo
}

internal object PipePipeSabrPlayerInfoProbe : SabrPlayerInfoProbe {
    private val localization = Localization("en", "US")
    private val contentCountry = ContentCountry("US")

    override fun fetch(
        videoId: String,
        profile: YoutubeSabrClientProfile,
        token: SabrTokenBundle,
    ): YoutubeSabrInfo = TypetypeYoutubeSessionPoTokenProvider.withToken(token) {
        YoutubeSabrProbe.fetchSabrInfo(videoId, profile, localization, contentCountry)
    }
}
