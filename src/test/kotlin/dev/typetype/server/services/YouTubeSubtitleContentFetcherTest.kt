package dev.typetype.server.services

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeSubtitleContentFetcherTest {
    @Test
    fun `fetcher sends browser context and accepts bounded WebVTT`() = runTest {
        var accept: String? = null
        var origin: String? = null
        val fetcher = fetcher(VTT, "text/vtt") { request ->
            accept = request.header("Accept")
            origin = request.header("Origin")
        }

        val result = fetcher.fetch(TIMED_TEXT_URL, YouTubeSubtitleFormat.Vtt)

        assertTrue(result is YouTubeSubtitleFetchResult.Ready)
        assertTrue(accept?.startsWith("text/vtt") == true)
        assertEquals("https://m.youtube.com", origin)
    }

    @Test
    fun `fetcher classifies throttle and expired URLs`() = runTest {
        assertEquals(
            YouTubeSubtitleFetchResult.Throttled,
            fetcher("throttled", code = 429).fetch(TIMED_TEXT_URL, YouTubeSubtitleFormat.Vtt),
        )
        assertEquals(
            YouTubeSubtitleFetchResult.Expired,
            fetcher("expired", code = 403).fetch(TIMED_TEXT_URL, YouTubeSubtitleFormat.Vtt),
        )
    }

    @Test
    fun `fetcher rejects an unexpected success payload`() = runTest {
        assertEquals(
            YouTubeSubtitleFetchResult.InvalidPayload,
            fetcher("<html>challenge</html>", "text/html")
                .fetch(TIMED_TEXT_URL, YouTubeSubtitleFormat.Vtt),
        )
    }

    private fun fetcher(
        body: String,
        contentType: String = "application/json",
        code: Int = 200,
        observe: (okhttp3.Request) -> Unit = {},
    ): OkHttpYouTubeSubtitleContentFetcher {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                observe(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody(contentType.toMediaType()))
                    .build()
            }
            .build()
        return OkHttpYouTubeSubtitleContentFetcher(client)
    }

    private companion object {
        const val VTT = "WEBVTT\n\n00:00.000 --> 00:01.000\nHello"
        const val TIMED_TEXT_URL = "https://www.youtube.com/api/timedtext?v=abcdefghijk&lang=en&fmt=vtt"
    }
}
