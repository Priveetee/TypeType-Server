package dev.typetype.server.routes

import dev.typetype.server.services.AndroidPlaybackService
import dev.typetype.server.services.AndroidPlaybackSession
import dev.typetype.server.services.AndroidPlaybackSessionLookup
import dev.typetype.server.services.AndroidSubtitleContentResult
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

    private fun io.ktor.server.application.Application.installRoute(handler: AndroidSubtitleHandler) {
        install(ContentNegotiation) { json() }
        routing {
            get("/subtitle") { handler.content(call, SESSION_ID, TRACK.id) }
        }
    }

    private fun fixture(
        owner: String,
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
            AndroidPlaybackSessionLookup.Active(AndroidPlaybackSession(holder, listOf(TRACK)))
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
