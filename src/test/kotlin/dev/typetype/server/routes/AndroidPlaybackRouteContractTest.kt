package dev.typetype.server.routes

import dev.typetype.server.services.AndroidDashManifestResult
import dev.typetype.server.services.AndroidPlaybackMediaResult
import dev.typetype.server.services.AndroidPlaybackService
import dev.typetype.server.services.AndroidPlaybackSession
import dev.typetype.server.services.AndroidPlaybackSessionLookup
import dev.typetype.server.services.AndroidSubtitleInventoryCoordinator
import dev.typetype.server.services.SabrSessionHolder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

class AndroidPlaybackRouteContractTest {
    @Test
    fun `dedicated Android manifest path does not use the web SABR namespace`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                androidPlaybackRoutes(
                    mockk(relaxed = true),
                    mockk(),
                    mockk(),
                    mockk(),
                    null,
                    null,
                    null,
                )
            }
        }

        val response = client.get("/android/youtube/playback/unknown/manifest.mpd")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `ready manifest returns dash xml with no store`() = testApplication {
        val fixture = fixture()
        every { fixture.service.lookup(SESSION_ID) } returns fixture.lookup
        coEvery { fixture.service.manifest(fixture.holder) } returns AndroidDashManifestResult.Ready(MPD, 1_000L)
        application {
            install(ContentNegotiation) { json() }
            routing { get("/manifest") { fixture.handler.manifest(call, SESSION_ID) } }
        }

        val response = client.get("/manifest")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(ContentType.parse("application/dash+xml"), response.contentType()?.withoutParameters())
        assertEquals(MPD, response.bodyAsText())
    }

    @Test
    fun `preparing manifest returns bounded json retry`() = testApplication {
        val fixture = fixture()
        every { fixture.service.lookup(SESSION_ID) } returns fixture.lookup
        coEvery { fixture.service.manifest(fixture.holder) } returns AndroidDashManifestResult.Preparing
        application {
            install(ContentNegotiation) { json() }
            routing { get("/manifest") { fixture.handler.manifest(call, SESSION_ID) } }
        }

        val response = client.get("/manifest")

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertTrue(response.bodyAsText().contains("\"retryAfterMs\":500"))
    }

    @Test
    fun `unknown and expired sessions are distinguished`() = testApplication {
        val fixture = fixture()
        every { fixture.service.lookup("unknown") } returns AndroidPlaybackSessionLookup.Unknown
        every { fixture.service.lookup("expired") } returns AndroidPlaybackSessionLookup.Expired
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/unknown") { fixture.handler.manifest(call, "unknown") }
                get("/expired") { fixture.handler.manifest(call, "expired") }
            }
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/unknown").status)
        assertEquals(HttpStatusCode.Gone, client.get("/expired").status)
    }

    @Test
    fun `stale media generation returns conflict`() = testApplication {
        val fixture = fixture()
        every { fixture.service.lookup(SESSION_ID) } returns fixture.lookup
        coEvery { fixture.service.segment(fixture.holder, 137, 1, 0L) } returns AndroidPlaybackMediaResult.StaleGeneration
        val media = AndroidPlaybackMediaHandler(fixture.service)
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/segment") { media.segment(call, SESSION_ID, 137, 1) }
            }
        }

        val response = client.get("/segment?session=$SESSION_ID&generation=0")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("android_playback_stale_generation"))
    }

    @Test
    fun `invalid complete index returns unprocessable entity`() = testApplication {
        val fixture = fixture()
        every { fixture.service.lookup(SESSION_ID) } returns fixture.lookup
        coEvery { fixture.service.manifest(fixture.holder) } returns AndroidDashManifestResult.Invalid("invalid index")
        application {
            install(ContentNegotiation) { json() }
            routing { get("/manifest") { fixture.handler.manifest(call, SESSION_ID) } }
        }

        val response = client.get("/manifest")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("android_playback_invalid_index"))
    }

    private fun fixture(): Fixture {
        val service = mockk<AndroidPlaybackService>()
        val audio = format(140, true)
        val video = format(137, false)
        val holder = mockk<SabrSessionHolder> {
            every { sessionToken } returns SESSION_ID
            every { key.videoId } returns "video"
            every { audioFormat } returns audio
            every { videoFormat } returns video
            every { activeGeneration() } returns 0L
        }
        val handler = AndroidPlaybackHandler(
            mockk(),
            mockk(),
            null,
            null,
            null,
            mockk<AndroidSubtitleInventoryCoordinator>(),
            service,
        )
        return Fixture(service, holder, handler, AndroidPlaybackSessionLookup.Active(AndroidPlaybackSession(holder, emptyList())))
    }

    private fun format(itag: Int, audio: Boolean): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { audioTrackId } returns null
        every { isAudio } returns audio
    }

    private data class Fixture(
        val service: AndroidPlaybackService,
        val holder: SabrSessionHolder,
        val handler: AndroidPlaybackHandler,
        val lookup: AndroidPlaybackSessionLookup.Active,
    )

    private companion object {
        const val SESSION_ID = "android-session"
        const val MPD = "<?xml version=\"1.0\"?><MPD/>"
    }
}
