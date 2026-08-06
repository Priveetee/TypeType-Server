package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BlockedStreamRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val blocked = BlockedService()
    private val streams: StreamService = mockk()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() {
            TestDatabase.setup()
        }
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    @Test
    fun `blocked video cannot be extracted through an equivalent url`() = testApplication {
        blocked.addVideo(TEST_USER_ID, "https://www.youtube.com/watch?v=blocked-video")
        application { installRoutes() }

        val response = get("https://youtu.be/blocked-video")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"content_blocked\""))
    }

    @Test
    fun `blocked channel cannot be extracted`() = testApplication {
        blocked.addChannel(TEST_USER_ID, "https://www.youtube.com/@blocked", "Blocked")
        coEvery { streams.getStreamInfo(any()) } returns ExtractionResult.Success(
            sabrResponse().copy(
                uploaderName = "Blocked",
                uploaderUrl = "https://m.youtube.com/@blocked/",
            ),
        )
        application { installRoutes() }

        val response = get("https://youtube.com/watch?v=channel-video")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"content_blocked\""))
    }

    @Test
    fun `authenticated stream filters blocked related videos and disables shared caching`() =
        testApplication {
            blocked.addChannel(TEST_USER_ID, "https://youtube.com/@blocked", "Blocked")
            coEvery { streams.getStreamInfo(any()) } returns ExtractionResult.Success(
                sabrResponse().copy(
                    relatedStreams = listOf(
                        testVideoItem().copy(
                            title = "Hidden",
                            uploaderName = "Blocked",
                            uploaderUrl = "https://www.youtube.com/@blocked",
                        ),
                        testVideoItem().copy(title = "Visible", url = "https://youtube.com/watch?v=visible-video"),
                    ),
                ),
            )
            application { installRoutes() }

            val response = get("https://youtube.com/watch?v=source-video")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertFalse(body.contains("\"title\":\"Hidden\""))
            assertTrue(body.contains("\"title\":\"Visible\""))
        }

    private fun io.ktor.server.application.Application.installRoutes() {
        install(ContentNegotiation) { json() }
        routing {
            streamRoutes(
                streamService = streams,
                authService = auth,
                blockedService = blocked,
            )
        }
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.get(url: String) =
        client.get("/streams/youtube/sabr?url=${java.net.URLEncoder.encode(url, Charsets.UTF_8)}") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }

    private fun sabrResponse() = testStreamResponse(
        videoOnlyStreams = listOf(testVideoStream().copy(deliveryMethod = "sabr")),
        audioStreams = listOf(testAudioStream(deliveryMethod = "sabr")),
    )
}
