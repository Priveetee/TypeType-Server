package dev.typetype.server

import dev.typetype.server.routes.proxyRoutes
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.YouTubeSubtitleService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeSubtitleProxyRoutesTest {
    private val proxyService: ProxyService = mockk(relaxed = true)

    @Test
    fun `YouTube subtitle proxy returns same-origin WebVTT`() = testApplication {
        val service = subtitleService("WEBVTT\n\n00:00.000 --> 00:01.000\nHello", "text/vtt")
        application {
            install(ContentNegotiation) { json() }
            routing { proxyRoutes(proxyService, service) }
        }

        val response = client.get("/proxy") {
            parameter("url", "https://www.youtube.com/api/timedtext?v=video&lang=en")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("text/vtt") == true)
        assertEquals("private, max-age=300", response.headers[HttpHeaders.CacheControl])
        assertTrue(response.bodyAsText().startsWith("WEBVTT"))
        coVerify(exactly = 0) { proxyService.pipe(any(), any(), any()) }
    }

    @Test
    fun `YouTube subtitle throttle returns a typed request id error`() = testApplication {
        var tokenRequest: Request? = null
        val service = subtitleService(
            """{"error":"throttled","code":"subtitle_upstream_throttled"}""",
            code = 429,
        ) { tokenRequest = it }
        application {
            installRequestObservability()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            configureStatusPages()
            routing { proxyRoutes(proxyService, service) }
        }

        val response = client.get("/proxy") {
            header(REQUEST_ID_HEADER, "subtitle-request-123")
            parameter("url", "https://www.youtube.com/api/timedtext?v=video&lang=en")
        }

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(null, response.headers[HttpHeaders.RetryAfter])
        assertEquals("subtitle-request-123", response.headers[REQUEST_ID_HEADER])
        assertEquals("subtitle-request-123", tokenRequest?.header(REQUEST_ID_HEADER))
        val body = response.bodyAsText()
        assertTrue(body.contains("\"code\":\"subtitle_upstream_throttled\""))
        assertTrue(body.contains("\"requestId\":\"subtitle-request-123\""))
        coVerify(exactly = 0) { proxyService.pipe(any(), any(), any()) }
    }

    private fun subtitleService(
        body: String,
        contentType: String = "application/json",
        code: Int = 200,
        observeRequest: (Request) -> Unit = {},
    ): YouTubeSubtitleService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                observeRequest(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody(contentType.toMediaType()))
                    .build()
            }
            .build()
        return YouTubeSubtitleService(client, "http://token")
    }
}
