package dev.typetype.server.services

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypetypeTokenYoutubeSessionClientTest {
    @Test
    fun `fetches mweb live manifest`() = runTest {
        var requestedVideoId: String? = null
        var requestedClient: String? = null
        val httpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requestedVideoId = chain.request().url.queryParameter("videoId")
            requestedClient = chain.request().url.queryParameter("client")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"hlsManifestUrl":"https://example.com/live.m3u8"}""".toResponseBody())
                .build()
        }).build()
        val client = TypetypeTokenYoutubeSessionClient("https://token.example/base/", httpClient)

        val result = client.fetchHlsManifestUrl("a b")

        assertEquals("https://example.com/live.m3u8", result)
        assertEquals("a b", requestedVideoId)
        assertEquals("MWEB", requestedClient)
    }
}
