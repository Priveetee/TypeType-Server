package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

class SabrSessionIdentityTest {
    @Test
    fun `fresh identities use unique coherent playback nonces`() {
        val formats = listOf(mockk<YoutubeSabrFormat>())
        val source = mockk<YoutubeSabrInfo>()
        every { source.profile } returns YoutubeSabrClientProfile.WEB
        every { source.videoId } returns "video"
        every { source.clientVersion } returns "1.2.3"
        every { source.visitorData } returns "visitor"
        every { source.serverAbrStreamingUrl } returns "https://example.com/sabr?cpn=stale&foo=bar"
        every { source.videoPlaybackUstreamerConfig } returns "config"
        every { source.formats } returns formats

        val first = SabrSessionIdentity.fresh(source)
        val second = SabrSessionIdentity.fresh(source)

        assertNotEquals(first.cpn, second.cpn)
        assertEquals(first.cpn, first.serverAbrStreamingUrl!!.toHttpUrl().queryParameter("cpn"))
        assertEquals(second.cpn, second.serverAbrStreamingUrl!!.toHttpUrl().queryParameter("cpn"))
        assertEquals("bar", first.serverAbrStreamingUrl!!.toHttpUrl().queryParameter("foo"))
        assertSame(formats[0], first.formats[0])
    }
}
