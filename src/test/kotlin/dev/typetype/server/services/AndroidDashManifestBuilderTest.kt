package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class AndroidDashManifestBuilderTest {
    @Test
    fun `five minute vod describes the complete presentation from zero`() {
        val manifest = manifest(
            audioSegments = timeline(300, 1_000L),
            videoSegments = timeline(150, 2_000L),
            durationMs = 300_000L,
        )

        val document = parse(manifest)
        val mpd = document.documentElement
        assertEquals("static", mpd.getAttribute("type"))
        assertEquals("PT300.000S", mpd.getAttribute("mediaPresentationDuration"))
        assertEquals("PT0S", document.getElementsByTagNameNS(DASH_NAMESPACE, "Period").item(0).attributes
            .getNamedItem("start").nodeValue)
        assertEquals(2, document.getElementsByTagNameNS(DASH_NAMESPACE, "SegmentTimeline").length)
    }

    @Test
    fun `manifest resources contain the session and generation with valid XML escaping`() {
        val manifest = manifest(timeline(3, 1_000L), timeline(3, 1_000L), 3_000L, generation = 7L)

        assertTrue(manifest.contains("session=session-token&amp;generation=7"))
        val template = parse(manifest).getElementsByTagNameNS(DASH_NAMESPACE, "SegmentTemplate").item(0)
        assertEquals(
            "/api/android/youtube/playback/session-token/137/segment/\$Number\$?session=session-token&generation=7",
            template.attributes.getNamedItem("media").nodeValue,
        )
    }

    @Test
    fun `audio and video may have different exact segment counts`() {
        val manifest = manifest(timeline(6, 500L), timeline(2, 1_500L), 3_000L)
        val document = parse(manifest)
        val templates = document.getElementsByTagNameNS(DASH_NAMESPACE, "SegmentTemplate")

        assertEquals("1", templates.item(0).attributes.getNamedItem("startNumber").nodeValue)
        assertEquals("1", templates.item(1).attributes.getNamedItem("startNumber").nodeValue)
        assertEquals(2, document.getElementsByTagNameNS(DASH_NAMESPACE, "S").length)
    }

    @Test
    fun `long vod manifest remains compact`() {
        val manifest = manifest(timeline(100_000, 1_000L), timeline(100_000, 1_000L), 100_000_000L)

        assertTrue(manifest.toByteArray().size < 2 * 1024 * 1024)
        assertFalse(manifest.contains("SegmentURL"))
        assertEquals(2, parse(manifest).getElementsByTagNameNS(DASH_NAMESPACE, "S").length)
    }

    private fun manifest(
        audioSegments: List<AndroidDashTimelineSegment>,
        videoSegments: List<AndroidDashTimelineSegment>,
        durationMs: Long,
        generation: Long = 0L,
    ): String = AndroidDashManifestBuilder.build(
        sessionId = "session-token",
        generation = generation,
        audio = AndroidDashTrack(format(140, audio = true), AndroidDashTimeline(1, audioSegments)),
        video = AndroidDashTrack(format(137, audio = false), AndroidDashTimeline(1, videoSegments)),
        durationMs = durationMs,
    )

    private fun timeline(count: Int, durationMs: Long): List<AndroidDashTimelineSegment> =
        List(count) { index -> AndroidDashTimelineSegment(index * durationMs, durationMs) }

    private fun format(itag: Int, audio: Boolean): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { isAudio } returns audio
        every { isVideo } returns !audio
        every { mimeType } returns if (audio) "audio/mp4; codecs=\"mp4a.40.2\"" else "video/mp4; codecs=\"avc1.640028\""
        every { bitrate } returns if (audio) 128_000 else 2_000_000
        every { width } returns if (audio) 0 else 1920
        every { height } returns if (audio) 0 else 1080
    }

    private fun parse(manifest: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream(manifest.toByteArray()))

    private companion object {
        const val DASH_NAMESPACE = "urn:mpeg:dash:schema:mpd:2011"
    }
}
