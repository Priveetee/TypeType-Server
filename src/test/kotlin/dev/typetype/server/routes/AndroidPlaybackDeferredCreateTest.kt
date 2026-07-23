package dev.typetype.server.routes

import dev.typetype.server.services.AndroidDashManifestResult
import dev.typetype.server.services.AndroidPlaybackCreateResult
import dev.typetype.server.services.AndroidPlaybackService
import dev.typetype.server.services.AndroidPlaybackSession
import dev.typetype.server.services.AndroidSubtitleInventoryCoordinator
import dev.typetype.server.services.AndroidSubtitleInventoryHandle
import dev.typetype.server.services.SabrPreparedInfo
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

class AndroidPlaybackDeferredCreateTest {
    @Test
    fun `deferred mode returns playable media while subtitle inventory is preparing`() = testApplication {
        val inventory = AndroidSubtitleInventoryHandle.preparing()
        val fixture = fixture(inventory)
        coEvery {
            fixture.service.create(
                VIDEO_ID,
                "guest",
                fixture.prepared,
                fixture.audio,
                fixture.video,
                inventory,
                true,
            )
        } returns AndroidPlaybackCreateResult.Created(
            AndroidPlaybackSession(fixture.holder, inventory, deferredSubtitles = true),
            AndroidDashManifestResult.Ready(MPD, 213_000L),
        )
        application { installRoute(fixture.handler) }

        val response = client.post("/playback") {
            contentType(ContentType.Application.Json)
            setBody("""{"subtitleMode":"deferred"}""")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"ready\":true"))
        assertTrue(body.contains("\"subtitles\":[]"))
        assertTrue(body.contains("\"status\":\"preparing\""))
        assertTrue(body.contains("\"retryAfterMs\":250"))
        assertTrue(body.contains("/subtitles\""))
    }

    @Test
    fun `omitted mode preserves inline failure behavior`() = testApplication {
        val inventory = AndroidSubtitleInventoryHandle.temporaryFailure()
        val fixture = fixture(inventory)
        application { installRoute(fixture.handler) }

        val response = client.post("/playback") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("android_subtitle_inventory_unavailable"))
        coVerify(exactly = 0) {
            fixture.service.create(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `deferred inventory failure does not block playable media`() = testApplication {
        val inventory = AndroidSubtitleInventoryHandle.temporaryFailure()
        val fixture = fixture(inventory)
        fixture.stubCreated(inventory, deferred = true)
        application { installRoute(fixture.handler) }

        val response = client.post("/playback") {
            contentType(ContentType.Application.Json)
            setBody("""{"subtitleMode":"deferred"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"unavailable\""))
    }

    @Test
    fun `omitted mode keeps ready subtitles inline`() = testApplication {
        val inventory = AndroidSubtitleInventoryHandle.ready(listOf(TRACK))
        val fixture = fixture(inventory)
        fixture.stubCreated(inventory, deferred = false)
        application { installRoute(fixture.handler) }

        val response = client.post("/playback") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"id\":\"track\""))
        assertTrue(body.contains("/subtitles/track.vtt"))
    }

    private fun io.ktor.server.application.Application.installRoute(handler: AndroidPlaybackHandler) {
        install(ContentNegotiation) { json() }
        routing { post("/playback") { handler.create(call, VIDEO_ID) } }
    }

    private fun fixture(inventory: AndroidSubtitleInventoryHandle): Fixture {
        val audio = format(140, audio = true, "audio/mp4; codecs=\"mp4a.40.2\"")
        val video = format(137, audio = false, "video/mp4; codecs=\"avc1.4d4028\"")
        val info = mockk<YoutubeSabrInfo> {
            every { formats } returns listOf(audio, video)
        }
        val prepared = SabrPreparedInfo(info, null)
        val store = mockk<SabrSessionStore>()
        coEvery { store.fetchInfo(VIDEO_ID, cachedFirst = true) } returns prepared
        val coordinator = mockk<AndroidSubtitleInventoryCoordinator>()
        every { coordinator.start(VIDEO_ID) } returns inventory
        val service = mockk<AndroidPlaybackService>()
        val holder = mockk<SabrSessionHolder> {
            every { sessionToken } returns SESSION_ID
            every { key.videoId } returns VIDEO_ID
            every { audioFormat } returns audio
            every { videoFormat } returns video
            every { activeGeneration() } returns 0L
        }
        val handler = AndroidPlaybackHandler(
            store,
            mockk<StreamService>(),
            null,
            null,
            null,
            coordinator,
            service,
        )
        return Fixture(handler, service, prepared, holder, audio, video)
    }

    private fun format(itag: Int, audio: Boolean, mimeType: String): YoutubeSabrFormat =
        mockk(relaxed = true) {
            every { this@mockk.itag } returns itag
            every { isAudio } returns audio
            every { isVideo } returns !audio
            every { this@mockk.mimeType } returns mimeType
            every { height } returns if (audio) 0 else 1080
            every { bitrate } returns if (audio) 128_000 else 4_000_000
            every { audioTrackId } returns null
        }

    private data class Fixture(
        val handler: AndroidPlaybackHandler,
        val service: AndroidPlaybackService,
        val prepared: SabrPreparedInfo,
        val holder: SabrSessionHolder,
        val audio: YoutubeSabrFormat,
        val video: YoutubeSabrFormat,
    ) {
        fun stubCreated(inventory: AndroidSubtitleInventoryHandle, deferred: Boolean) {
            coEvery {
                service.create(
                    VIDEO_ID,
                    "guest",
                    prepared,
                    audio,
                    video,
                    inventory,
                    deferred,
                )
            } returns AndroidPlaybackCreateResult.Created(
                AndroidPlaybackSession(holder, inventory, deferred),
                AndroidDashManifestResult.Ready(MPD, 213_000L),
            )
        }
    }

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
        const val SESSION_ID = "android-session"
        const val MPD = "<?xml version=\"1.0\"?><MPD/>"
        val TRACK = mockk<dev.typetype.server.services.AndroidSubtitleTrack>(relaxed = true) {
            every { id } returns "track"
            every { languageTag } returns "en"
            every { displayLanguageName } returns "English"
            every { isAutoGenerated } returns false
        }
    }
}
