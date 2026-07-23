package dev.typetype.server.routes

import dev.typetype.server.services.AndroidPlaybackService
import dev.typetype.server.services.AndroidPlaybackSession
import dev.typetype.server.services.AndroidPlaybackSessionLookup
import dev.typetype.server.services.AndroidSubtitleContentResult
import dev.typetype.server.services.AndroidSubtitleInventoryHandle
import dev.typetype.server.services.AndroidSubtitleService
import dev.typetype.server.services.AndroidSubtitleTrack
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionPurpose
import io.ktor.client.request.bearerAuth
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidSubtitleRouteTest {
    @Test
    fun `session owner receives complete WebVTT with no store`() = testApplication {
        val fixture = fixture("user")
        coEvery { fixture.subtitles.content(VIDEO_ID, TRACK) } returns AndroidSubtitleContentResult.Ready(VTT)
        application { installRoute(fixture.handler) }

        val response = client.get("/subtitle") { bearerAuth("test-jwt") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.parse("text/vtt"), response.contentType()?.withoutParameters())
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(VTT.decodeToString(), response.bodyAsText())
    }

    @Test
    fun `another account cannot read a session subtitle`() = testApplication {
        val fixture = fixture("owner", authenticatedUser = "other")
        application { installRoute(fixture.handler) }

        val response = client.get("/subtitle") { bearerAuth("test-jwt") }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("android_subtitle_not_found"))
        coVerify(exactly = 0) { fixture.subtitles.content(any(), any()) }
    }

    @Test
    fun `bounded upstream failure returns typed service unavailable`() = testApplication {
        val fixture = fixture("user")
        coEvery { fixture.subtitles.content(VIDEO_ID, TRACK) } returns AndroidSubtitleContentResult.TemporaryFailure
        application { installRoute(fixture.handler) }

        val response = client.get("/subtitle") { bearerAuth("test-jwt") }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("android_subtitle_upstream_unavailable"))
    }

    @Test
    fun `preparing inventory returns a bounded retry response`() = testApplication {
        val fixture = fixture("user", AndroidSubtitleInventoryHandle.preparing())
        application { installRoute(fixture.handler) }

        val response = client.get("/inventory") { bearerAuth("test-jwt") }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("1", response.headers[HttpHeaders.RetryAfter])
        assertTrue(response.bodyAsText().contains("\"status\":\"preparing\""))
        assertTrue(response.bodyAsText().contains("\"retryAfterMs\":250"))
    }

    @Test
    fun `ready inventory returns playable tracks and supports empty lists`() = testApplication {
        val ready = fixture("user", AndroidSubtitleInventoryHandle.ready(listOf(TRACK)))
        application { installRoute(ready.handler) }

        val response = client.get("/inventory") { bearerAuth("test-jwt") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertTrue(response.bodyAsText().contains("\"status\":\"ready\""))
        assertTrue(response.bodyAsText().contains("/subtitles/${TRACK.id}.vtt"))
    }

    @Test
    fun `empty inventory is a successful ready result`() = testApplication {
        val fixture = fixture("user", AndroidSubtitleInventoryHandle.ready(emptyList()))
        application { installRoute(fixture.handler) }

        val response = client.get("/inventory") { bearerAuth("test-jwt") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"tracks\":[]"))
    }

    @Test
    fun `failed inventory returns a typed temporary failure`() = testApplication {
        val fixture = fixture("user", AndroidSubtitleInventoryHandle.temporaryFailure())
        application { installRoute(fixture.handler) }

        val response = client.get("/inventory") { bearerAuth("test-jwt") }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("1", response.headers[HttpHeaders.RetryAfter])
        assertTrue(response.bodyAsText().contains("android_subtitle_inventory_unavailable"))
    }

    @Test
    fun `unknown and expired inventory sessions keep distinct semantics`() = testApplication {
        val fixture = fixture("user")
        every { fixture.playback.lookup("unknown") } returns AndroidPlaybackSessionLookup.Unknown
        every { fixture.playback.lookup("expired") } returns AndroidPlaybackSessionLookup.Expired
        application { installRoute(fixture.handler) }

        val unknown = client.get("/inventory/unknown") { bearerAuth("test-jwt") }
        val expired = client.get("/inventory/expired") { bearerAuth("test-jwt") }

        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertTrue(unknown.bodyAsText().contains("android_playback_not_found"))
        assertEquals(HttpStatusCode.Gone, expired.status)
        assertTrue(expired.bodyAsText().contains("android_playback_expired"))
    }

    @Test
    fun `another account cannot read a session inventory`() = testApplication {
        val fixture = fixture("owner", authenticatedUser = "other")
        application { installRoute(fixture.handler) }

        val response = client.get("/inventory") { bearerAuth("test-jwt") }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("android_subtitle_not_found"))
    }

    private fun io.ktor.server.application.Application.installRoute(handler: AndroidSubtitleHandler) {
        install(ContentNegotiation) { json() }
        routing {
            get("/subtitle") { handler.content(call, SESSION_ID, TRACK.id) }
            get("/inventory") { handler.inventory(call, SESSION_ID) }
            get("/inventory/{sessionId}") {
                handler.inventory(call, requireNotNull(call.parameters["sessionId"]))
            }
        }
    }

    private fun fixture(
        owner: String,
        inventory: AndroidSubtitleInventoryHandle = AndroidSubtitleInventoryHandle.ready(listOf(TRACK)),
        authenticatedUser: String = owner,
    ): Fixture {
        val playback = mockk<AndroidPlaybackService>()
        val holder = mockk<SabrSessionHolder> {
            every { key } returns SabrSessionKey(
                VIDEO_ID,
                owner,
                140,
                null,
                137,
                0L,
                SabrSessionPurpose.ANDROID_PLAYBACK,
            )
        }
        every { playback.lookup(SESSION_ID) } returns
            AndroidPlaybackSessionLookup.Active(AndroidPlaybackSession(holder, inventory, deferredSubtitles = true))
        val subtitles = mockk<AndroidSubtitleService>()
        val handler = AndroidSubtitleHandler(
            playback,
            subtitles,
            AuthService.fixed(authenticatedUser),
            null,
            null,
        )
        return Fixture(handler, playback, subtitles)
    }

    private data class Fixture(
        val handler: AndroidSubtitleHandler,
        val playback: AndroidPlaybackService,
        val subtitles: AndroidSubtitleService,
    )

    private companion object {
        const val SESSION_ID = "android-session"
        const val VIDEO_ID = "dQw4w9WgXcQ"
        val VTT = "WEBVTT\n\n00:00.000 --> 00:01.000\nHello\n".toByteArray()
        val TRACK = AndroidSubtitleTrack(
            "track",
            "en",
            "English",
            false,
            "https://www.youtube.com/api/timedtext?v=$VIDEO_ID&lang=en".toHttpUrl(),
        )
    }
}
