package dev.typetype.server.services

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
    fun providerForceRefreshUsesVisitorRefresh(): Unit {
        val recorder = PotokenRequestRecorder()
        val client = TypetypeTokenSabrTokenClient("https://token.example", recorder.client)
        val provider = TypetypeTokenSabrPoTokenProvider(client)
        val fetch = TypetypeTokenSabrPoTokenProvider::class.java.getDeclaredMethod(
            "fetch",
            String::class.java,
            java.lang.Boolean.TYPE,
        )

        fetch.isAccessible = true
        val token = fetch.invoke(provider, "video", true) as ByteArray?

        assertNotNull(token)
        val url = recorder.urls.single()
        assertEquals("video", url.queryParameter("videoId"))
        assertEquals("true", url.queryParameter("refresh"))
        assertNull(url.queryParameter("refreshVideo"))
    }

    private class PotokenRequestRecorder {
        val urls = mutableListOf<okhttp3.HttpUrl>()
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                urls += chain.request().url
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(TOKEN_JSON.toResponseBody())
                    .build()
            })
            .build()
    }

    private companion object {
        const val TOKEN_JSON =
            """{"visitorBoundPoToken":"AQ","visitorData":"visitor","videoBoundPoToken":"Ag"}"""
    }
}
