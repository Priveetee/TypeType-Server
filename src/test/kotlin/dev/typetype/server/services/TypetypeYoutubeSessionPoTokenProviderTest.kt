package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class TypetypeYoutubeSessionPoTokenProviderTest {
    @Test
    fun `exposes the session token only inside its scope`() {
        TypetypeYoutubeSessionPoTokenProvider.withToken(token("visitor", "player-token")) {
            assertEquals("visitor", currentToken()?.visitorData)
            assertEquals("player-token", currentToken()?.poToken)
        }

        assertNull(currentToken())
    }

    @Test
    fun `restores the outer token after a nested scope`() {
        TypetypeYoutubeSessionPoTokenProvider.withToken(token("outer", "outer-token")) {
            TypetypeYoutubeSessionPoTokenProvider.withToken(token("inner", "inner-token")) {
                assertEquals("inner", currentToken()?.visitorData)
            }
            assertEquals("outer", currentToken()?.visitorData)
        }

        assertNull(currentToken())
    }

    @Test
    fun `clears the token when the scoped call fails`() {
        runCatching {
            TypetypeYoutubeSessionPoTokenProvider.withToken(token("visitor", "player-token")) {
                error("failed")
            }
        }

        assertNull(currentToken())
    }

    private fun currentToken() = TypetypeYoutubeSessionPoTokenProvider.getSessionPoToken(
        "MWEB",
        "2.20260801.00.00",
        "test-user-agent",
        Localization("en", "US"),
        ContentCountry("US"),
        false,
    )

    private fun token(visitorData: String, playerToken: String) = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = playerToken,
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = visitorData,
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
