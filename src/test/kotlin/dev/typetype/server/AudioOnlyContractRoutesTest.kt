package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.audioOnlyContractRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AuthService
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
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AudioOnlyContractRoutesTest {
    private val streamService: StreamService = mockk()
    private val tokenService = AudioOnlyMediaTokenService("test-secret")
    private val adminSettings = AdminSettingsService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `GET audio-only returns signed backend source and selected metadata`() = testApplication {
        val english = testAudioStream(audioLocale = "en", bitrate = 128, itag = 140)
        val original = testAudioStream(audioLocale = "ja", audioTrackName = "Original", bitrate = 160, itag = 141)
        val hls = testAudioStream(url = "https://manifest.googlevideo.com/api/manifest/hls/test", bitrate = 256)
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(hls, english, original))
        )
        installContractApp()

        val response = client.get("/streams/audio-only?url=https://youtube.com/watch?v=test&preferOriginal=true")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"src\":\"/streams/audio-only/source?token="))
        assertFalse(body.contains("googlevideo"))
        assertTrue(body.contains("\"kind\":\"progressive\""))
        assertTrue(body.contains("\"mimeType\":\"audio/mp4\""))
        assertTrue(body.contains("\"bitrate\":160"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `GET audio-only uses authenticated YouTube session first`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Failure("public path")
        application {
            install(ContentNegotiation) { json() }
            routing {
                audioOnlyContractRoutes(
                    streamService,
                    tokenService,
                    AuthService.fixed(TEST_USER_ID),
                    { _, _ -> ExtractionResult.Success(testStreamResponse()) },
                )
            }
        }

        val response = client.get("/streams/audio-only?url=https://youtube.com/watch?v=test") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 0) { streamService.getStreamInfo(any()) }
    }

    @Test
    fun `GET audio-only returns 422 when no audio stream is available`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = emptyList())
        )
        installContractApp()

        val response = client.get("/streams/audio-only?url=https://youtube.com/watch?v=test")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("No audio-only stream is available"))
    }

    @Test
    fun `GET audio-only blocks anonymous request when guests are disabled`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        installContractApp(AuthService("test-auth"), adminSettings)

        val response = client.get("/streams/audio-only?url=https://youtube.com/watch?v=test")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installContractApp(
        authService: AuthService? = null,
        settings: AdminSettingsService? = null,
    ): Unit = application {
        install(ContentNegotiation) { json() }
        routing { audioOnlyContractRoutes(streamService, tokenService, authService, adminSettingsService = settings) }
    }
}
