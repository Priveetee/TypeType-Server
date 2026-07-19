package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRecoverableException
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

class SabrUnauthorizedResponseRecoveryTest {
    @Test
    fun rejectsVideoTokenFromDifferentVisitorSession(): Unit {
        val (holder, state) = holder(visitorData = "visitor-a", currentToken = byteArrayOf(1))
        val recovery = SabrUnauthorizedResponseRecovery { bundle("visitor-b", byteArrayOf(2)) }

        assertThrows(SabrRecoverableException::class.java) { recovery.verify(holder) }

        verify(exactly = 0) { state.setPoToken(any()) }
    }

    @Test
    fun rejectsUnchangedVideoToken(): Unit {
        val token = byteArrayOf(1)
        val (holder, state) = holder(visitorData = "visitor-a", currentToken = token)
        val recovery = SabrUnauthorizedResponseRecovery { bundle("visitor-a", token) }

        assertThrows(SabrRecoverableException::class.java) { recovery.verify(holder) }

        verify(exactly = 0) { state.setPoToken(any()) }
    }

    @Test
    fun appliesFreshVideoTokenFromSameVisitorSession(): Unit {
        val freshToken = byteArrayOf(2)
        val refreshed = bundle("visitor-a", freshToken)
        val (holder, state) = holder(visitorData = "visitor-a", currentToken = byteArrayOf(1))
        val recovery = SabrUnauthorizedResponseRecovery { refreshed }

        recovery.verify(holder)

        verify(exactly = 1) { state.setPoToken(match { it.contentEquals(freshToken) }) }
        verify(exactly = 1) { holder.playerContextToken = refreshed }
    }

    private fun holder(
        visitorData: String,
        currentToken: ByteArray,
    ): Pair<SabrSessionHolder, YoutubeSabrStreamState> {
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val info = mockk<YoutubeSabrInfo>()
        val holder = mockk<SabrSessionHolder>(relaxed = true)
        every { state.poToken } returns currentToken
        every { session.streamState } returns state
        every { session.diagnosticTrace } returns "response n=4 http=403 segments=count=0"
        every { info.videoId } returns "video"
        every { info.visitorData } returns visitorData
        every { holder.session } returns session
        every { holder.info } returns info
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 0L)
        every { holder.markUnauthorizedRefreshAttempted() } returns true
        return holder to state
    }

    private fun bundle(visitorData: String, videoToken: ByteArray): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = "visitor-token",
        visitorBoundPoTokenBytes = byteArrayOf(9),
        visitorData = visitorData,
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = videoToken,
    )
}
