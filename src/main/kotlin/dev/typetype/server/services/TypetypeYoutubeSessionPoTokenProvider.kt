package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoTokenProvider

internal object TypetypeYoutubeSessionPoTokenProvider : YoutubeSessionPoTokenProvider {
    private val scopedToken = ThreadLocal<YoutubeSessionPoToken>()

    fun <T> withToken(token: SabrTokenBundle, block: () -> T): T {
        val previous = scopedToken.get()
        val sessionToken = YoutubeSessionPoToken(token.visitorData, token.visitorBoundPoToken)
        scopedToken.set(sessionToken)
        return try {
            block()
        } finally {
            if (previous == null) scopedToken.remove() else scopedToken.set(previous)
        }
    }

    override fun getSessionPoToken(
        clientName: String,
        localization: Localization,
        contentCountry: ContentCountry,
        loggedIn: Boolean,
    ): YoutubeSessionPoToken? = scopedToken.get()
}
