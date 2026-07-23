package dev.typetype.server.services

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class AndroidSubtitleHttpClientTest {
    @Test
    fun `HTTP 429 falls back to direct egress and requests WebVTT`() = runTest {
        val primaryCalls = AtomicInteger()
        val directCalls = AtomicInteger()
        val primary = client {
            primaryCalls.incrementAndGet()
            response(it, 429, "")
        }
        val direct = client {
            directCalls.incrementAndGet()
            assertEquals("vtt", it.url.queryParameter("fmt"))
            assertEquals("text/vtt", it.header("Accept"))
            response(it, 200, VTT.decodeToString())
        }

        val result = AndroidSubtitleHttpClient(primary, direct).fetch(SOURCE_URL)

        assertArrayEquals(VTT, (result as AndroidSubtitleUpstreamResult.Ready).bytes)
        assertEquals(1, primaryCalls.get())
        assertEquals(1, directCalls.get())
    }

    @Test
    fun `permanent upstream rejection does not retry another egress`() = runTest {
        val directCalls = AtomicInteger()
        val primary = client { response(it, 404, "") }
        val direct = client {
            directCalls.incrementAndGet()
            response(it, 200, VTT.decodeToString())
        }

        val result = AndroidSubtitleHttpClient(primary, direct).fetch(SOURCE_URL)

        assertEquals(AndroidSubtitleUpstreamResult.Unavailable, result)
        assertEquals(0, directCalls.get())
    }

    @Test
    fun `invalid successful body is not exposed as WebVTT`() = runTest {
        val client = client { response(it, 200, "<xml/>") }

        val result = AndroidSubtitleHttpClient(client, client).fetch(SOURCE_URL)

        assertEquals(AndroidSubtitleUpstreamResult.Unavailable, result)
    }

    @Test
    fun `temporary failure on both egress paths remains typed`() = runTest {
        val client = client { response(it, 503, "") }

        val result = AndroidSubtitleHttpClient(client, client).fetch(SOURCE_URL)

        assertEquals(AndroidSubtitleUpstreamResult.TemporaryFailure, result)
    }

    @Test
    fun `temporary failure is not duplicated without a configured proxy`() = runTest {
        val calls = AtomicInteger()
        val client = client {
            calls.incrementAndGet()
            response(it, 429, "")
        }

        val result = AndroidSubtitleHttpClient(client, null).fetch(SOURCE_URL)

        assertEquals(AndroidSubtitleUpstreamResult.TemporaryFailure, result)
        assertEquals(1, calls.get())
    }

    private fun client(responder: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain -> responder(chain.request()) }
            .build()

    private fun response(request: Request, status: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message("test")
            .body(body.toResponseBody("text/vtt".toMediaType()))
            .build()

    private companion object {
        val SOURCE_URL = "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&fmt=ttml".toHttpUrl()
        val VTT = "WEBVTT\n\n00:00.000 --> 00:01.000\nHello\n".toByteArray()
    }
}
