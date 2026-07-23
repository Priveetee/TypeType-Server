package dev.typetype.server.routes

import dev.typetype.server.services.AndroidDashManifestResult
import dev.typetype.server.services.AndroidPlaybackCreateResult
import dev.typetype.server.services.AndroidPlaybackService
import dev.typetype.server.services.AndroidPlaybackSession
import dev.typetype.server.services.AndroidSubtitleInventoryCoordinator
import dev.typetype.server.services.AndroidSubtitleInventoryHandle
import dev.typetype.server.services.AndroidSubtitleInventorySnapshot
import dev.typetype.server.services.AndroidSubtitleTrack
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

class AndroidPlaybackSubtitleBootstrapTest {
    @Test
    fun `creation waits for the authoritative descriptor catalog`() = testApplication {
        val inventory = AndroidSubtitleInventoryHandle.preparing()
        val fixture = fixture(inventory)
        fixture.stubCreated(listOf(TRACK), AndroidDashManifestResult.Ready(MPD, 213_000L))
        application { installRoute(fixture.handler) }

        coroutineScope {
            val pending = async {
                client.post("/playback") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"subtitleMode":"deferred"}""")
                }
            }
            yield()
            assertFalse(pending.isCompleted)

            inventory.complete(AndroidSubtitleInventorySnapshot.Ready(listOf(TRACK)))
            val response = pending.await()
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("\"subtitles\":[{\"id\":\"track\""))
            assertTrue(body.contains("/subtitles/track.vtt"))
            assertFalse(body.contains("subtitleInventory"))
        }
    }

    @Test
    fun `creation fails when a complete descriptor catalog is unavailable`() = testApplication {
        val fixture = fixture(AndroidSubtitleInventoryHandle.temporaryFailure())
        application { installRoute(fixture.handler) }

        val response = client.post("/playback") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("android_subtitle_inventory_unavailable"))
        coVerify(exactly = 0) { fixture.service.create(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `video without captions returns an authoritative empty catalog`() = testApplication {
        val fixture = fixture(AndroidSubtitleInventoryHandle.ready(emptyList()))
        fixture.stubCreated(emptyList(), AndroidDashManifestResult.Ready(MPD, 213_000L))
        application { installRoute(fixture.handler) }

        val response = client.post("/playback") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"subtitles\":[]"))
    }

    @Test
    fun `preparing media response still contains every subtitle descriptor`() = testApplication {
        val fixture = fixture(AndroidSubtitleInventoryHandle.ready(listOf(TRACK)))
        fixture.stubCreated(listOf(TRACK), AndroidDashManifestResult.Preparing)
        application { installRoute(fixture.handler) }

        val response = client.post("/playback") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertTrue(response.bodyAsText().contains("\"id\":\"track\""))
        assertTrue(response.bodyAsText().contains("\"retryAfterMs\":500"))
    }

    private fun io.ktor.server.application.Application.installRoute(handler: AndroidPlaybackHandler) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
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
        fun stubCreated(
            subtitles: List<AndroidSubtitleTrack>,
            manifest: AndroidDashManifestResult,
        ) {
            coEvery {
                service.create(VIDEO_ID, "guest", prepared, audio, video, subtitles)
            } returns AndroidPlaybackCreateResult.Created(
                AndroidPlaybackSession(holder, subtitles),
                manifest,
            )
        }
    }

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
        const val SESSION_ID = "android-session"
        const val MPD = "<?xml version=\"1.0\"?><MPD/>"
        val TRACK = mockk<AndroidSubtitleTrack>(relaxed = true) {
            every { id } returns "track"
            every { languageTag } returns "en"
            every { displayLanguageName } returns "English"
            every { isAutoGenerated } returns false
        }
    }
}
