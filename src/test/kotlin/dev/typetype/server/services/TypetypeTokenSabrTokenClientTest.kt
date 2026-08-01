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
    fun providerFetchesVideoTokenWithoutRefresh(): Unit {
        val recorder = PotokenRequestRecorder()
        val client = TypetypeTokenSabrTokenClient("https://token.example", recorder.client)
        val provider = TypetypeTokenSabrPoTokenProvider(client)
        val token = provider.getPoToken(info("visitor"), mockk())

        assertNotNull(token)
        assertArrayEquals(byteArrayOf(2), token)
        val url = recorder.urls.single()
        assertEquals("video", url.queryParameter("videoId"))
        assertNull(url.queryParameter("refresh"))
        assertNull(url.queryParameter("refreshVideo"))
    }

    @Test
    fun providerRejectsVideoTokenFromDifferentVisitorSession(): Unit {
        val recorder = PotokenRequestRecorder(MISMATCHED_TOKEN_JSON)
        val provider = TypetypeTokenSabrPoTokenProvider(
            TypetypeTokenSabrTokenClient("https://token.example", recorder.client),
        )

        val error = assertThrows(SabrRecoverableException::class.java) {
            provider.getPoToken(info("visitor"), mockk())
        }

        assertEquals(SABR_TOKEN_BINDING_FAILURE, error.message)
        val url = recorder.urls.single()
        assertNull(url.queryParameter("refresh"))
        assertNull(url.queryParameter("refreshVideo"))
    }

    private fun info(expectedVisitorData: String): YoutubeSabrInfo = mockk {
        every { videoId } returns "video"
        every { visitorData } returns expectedVisitorData
    }

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
    }
}
