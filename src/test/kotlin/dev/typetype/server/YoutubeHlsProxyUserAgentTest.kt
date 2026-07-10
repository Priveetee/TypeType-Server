package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.OkHttpProxyService
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YoutubeHlsProxyUserAgentTest {
    @Test
    fun `googlevideo media uses the extractor mweb user agent`() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals(OkHttpProxyService.YOUTUBE_MWEB_USER_AGENT, request.header("User-Agent"))
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(byteArrayOf(1).toResponseBody())
                .build()
        }.build()
        val service = OkHttpProxyService(client)

        val result = service.pipe(
            url = "https://redirector.googlevideo.com/videoplayback",
            rangeHeader = null,
            domandBid = null,
        )

        require(result is ExtractionResult.Success)
        assertEquals(200, result.data.status)
        result.data.close()
    }
}
