package dev.typetype.server.routes

import dev.typetype.server.services.AndroidDashManifestResult
import dev.typetype.server.services.AndroidSubtitleTrack
import dev.typetype.server.services.AndroidPlaybackSession
import dev.typetype.server.services.AndroidSubtitleInventoryHandle
import dev.typetype.server.services.SabrSessionHolder
import io.mockk.every
import io.mockk.mockk
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

class AndroidPlaybackSubtitleModelsTest {
    @Test
    fun `subtitle resources stay stable when the playback generation changes`() {
        var generation = 0L
        val holder = mockk<SabrSessionHolder> {
            every { sessionToken } returns "session"
            every { key.videoId } returns "video"
            every { audioFormat } returns format(140)
            every { videoFormat } returns format(137)
            every { activeGeneration() } answers { generation }
        }
        val track = AndroidSubtitleTrack(
            "track-id",
            "en",
            "English",
            false,
            "https://www.youtube.com/api/timedtext?v=video&lang=en".toHttpUrl(),
        )

        val session = AndroidPlaybackSession(
            holder,
            AndroidSubtitleInventoryHandle.ready(listOf(track)),
            deferredSubtitles = true,
        )
        val initial = session.toAndroidPlaybackResponse(MANIFEST)
        generation = 1L
        val afterSeek = session.toAndroidPlaybackResponse(MANIFEST)

        assertEquals(0L, initial.generation)
        assertEquals(1L, afterSeek.generation)
        assertEquals(initial.subtitles, afterSeek.subtitles)
        assertEquals(
            "/api/android/youtube/playback/session/subtitles/track-id.vtt",
            initial.subtitles.single().url,
        )
        assertEquals("ready", initial.subtitleInventory?.status)
        assertEquals(
            "/api/android/youtube/playback/session/subtitles",
            initial.subtitleInventory?.url,
        )
    }

    private fun format(itag: Int): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { audioTrackId } returns null
    }

    private companion object {
        val MANIFEST = AndroidDashManifestResult.Ready("<MPD/>", 1_000L)
    }
}
