package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRecoverableException
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

class TypetypeTokenSabrTokenClientTest {

    @Test
    fun forceRefreshUsesVisitorRefresh(): Unit {
        val recorder = PotokenRequestRecorder()
        val client = TypetypeTokenSabrTokenClient("https://token.example/base/", recorder.client)

        val token = client.fetch("a b", forceRefresh = true)

        assertNotNull(token)
        val url = recorder.urls.single()
        assertEquals("a b", url.queryParameter("videoId"))
        assertEquals("true", url.queryParameter("refresh"))
        assertNull(url.queryParameter("refreshVideo"))
    }

    @Test
    fun refreshVideoUsesVideoRefreshOnly(): Unit {
        val recorder = PotokenRequestRecorder()
        val client = TypetypeTokenSabrTokenClient("https://token.example", recorder.client)

        val token = client.fetch("video", refreshVideo = true)

        assertNotNull(token)
        val url = recorder.urls.single()
        assertEquals("video", url.queryParameter("videoId"))
        assertNull(url.queryParameter("refresh"))
        assertEquals("true", url.queryParameter("refreshVideo"))
    }

    @Test
    fun providerForceRefreshUsesVideoRefreshOnly(): Unit {
        val recorder = PotokenRequestRecorder()
        val client = TypetypeTokenSabrTokenClient("https://token.example", recorder.client)
        val provider = TypetypeTokenSabrPoTokenProvider(client)
        val token = provider.getPoToken(info("visitor"), mockk(), true)

        assertNotNull(token)
        assertArrayEquals(byteArrayOf(2), token)
        val url = recorder.urls.single()
        assertEquals("video", url.queryParameter("videoId"))
        assertNull(url.queryParameter("refresh"))
        assertEquals("true", url.queryParameter("refreshVideo"))
    }

    @Test
    fun providerRejectsVideoTokenFromDifferentVisitorSession(): Unit {
        val recorder = PotokenRequestRecorder(MISMATCHED_TOKEN_JSON)
        val provider = TypetypeTokenSabrPoTokenProvider(
            TypetypeTokenSabrTokenClient("https://token.example", recorder.client),
        )

        val error = assertThrows(SabrRecoverableException::class.java) {
            provider.getPoToken(info("visitor"), mockk(), true)
        }

        assertEquals(SABR_TOKEN_BINDING_FAILURE, error.message)
        val url = recorder.urls.single()
        assertNull(url.queryParameter("refresh"))
        assertEquals("true", url.queryParameter("refreshVideo"))
    }

    @Test
    fun playerContextProviderRefreshesVisitorAndPlayerTokenAtomically(): Unit {
        val initial = bundle("old-visitor", "old-player")
        val recorder = PotokenRequestRecorder(FRESH_TOKEN_JSON)
        val provider = TypetypeTokenSabrPlayerContextProvider(
            TypetypeTokenSabrTokenClient("https://token.example", recorder.client),
            initial,
        )

        val cached = provider.getPlayerContext("video", YoutubeSabrClientProfile.WEB)
        val refreshed = provider.getPlayerContext("video", YoutubeSabrClientProfile.WEB, true)

        assertEquals("old-visitor", cached.visitorData)
        assertEquals("old-player", cached.playerPoToken)
        assertEquals("fresh-visitor", refreshed.visitorData)
        assertEquals("Aw", refreshed.playerPoToken)
        assertEquals("fresh-visitor", provider.tokenFor(info("fresh-visitor"))?.visitorData)
        val url = recorder.urls.single()
        assertEquals("true", url.queryParameter("refresh"))
        assertNull(url.queryParameter("refreshVideo"))
    }

    private fun info(expectedVisitorData: String): YoutubeSabrInfo = mockk {
        every { videoId } returns "video"
        every { visitorData } returns expectedVisitorData
    }

    private fun bundle(visitorData: String, playerToken: String): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = playerToken,
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = visitorData,
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )

    private class PotokenRequestRecorder(tokenJson: String = TOKEN_JSON) {
        val urls = mutableListOf<okhttp3.HttpUrl>()
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                urls += chain.request().url
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(tokenJson.toResponseBody())
                    .build()
            })
            .build()
    }

    private companion object {
        const val TOKEN_JSON =
            """{"visitorBoundPoToken":"AQ","visitorData":"visitor","videoBoundPoToken":"Ag"}"""
        const val MISMATCHED_TOKEN_JSON =
            """{"visitorBoundPoToken":"AQ","visitorData":"other-visitor","videoBoundPoToken":"Ag"}"""
        const val FRESH_TOKEN_JSON =
            """{"visitorBoundPoToken":"Aw","visitorData":"fresh-visitor","videoBoundPoToken":"BA"}"""
    }
}
