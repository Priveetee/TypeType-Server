package dev.typetype.server

import dev.typetype.server.services.NativeManifestBuilder
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

class NativeManifestBuilderCoreTest {
    @Test
    fun `build includes progressive video representation with segment base`() {
        val video = mockk<VideoStream>()
        every { video.getCodec() } returns "avc1.640028"
        every { video.getItagItem() } returns mockk(relaxed = true)
        every { video.getBitrate() } returns 1200000
        every { video.getWidth() } returns 1920
        every { video.getHeight() } returns 1080
        every { video.deliveryMethod } returns DeliveryMethod.PROGRESSIVE_HTTP
        every { video.getContent() } returns "https://example.com/videoplayback?id=1"
        every { video.getIndexStart() } returns 221
        every { video.getIndexEnd() } returns 893
        every { video.getInitStart() } returns 0
        every { video.getInitEnd() } returns 220

        val manifest = NativeManifestBuilder.build(videos = listOf(video), audios = emptyList(), duration = 300)

        assertTrue(manifest.contains("mediaPresentationDuration=\"PT300S\""))
        assertTrue(manifest.contains("<AdaptationSet mimeType=\"video/mp4\""))
        assertTrue(manifest.contains("<Representation id=\"v-0\""))
        assertTrue(manifest.contains("<BaseURL>../proxy?url="))
        assertTrue(manifest.contains("<SegmentBase indexRange=\"221-893\""))
        assertTrue(manifest.contains("<Initialization range=\"0-220\""))
    }

    @Test
    fun `build omits segment base when index range is missing`() {
        val video = mockk<VideoStream>()
        every { video.getCodec() } returns "vp09.00.51.08"
        every { video.getItagItem() } returns mockk(relaxed = true)
        every { video.getBitrate() } returns 800000
        every { video.getWidth() } returns 1280
        every { video.getHeight() } returns 720
        every { video.deliveryMethod } returns DeliveryMethod.PROGRESSIVE_HTTP
        every { video.getContent() } returns "https://example.com/videoplayback?id=2"
        every { video.getIndexStart() } returns 0
        every { video.getIndexEnd() } returns 0
        every { video.getInitStart() } returns 0
        every { video.getInitEnd() } returns 0

        val manifest = NativeManifestBuilder.build(videos = listOf(video), audios = emptyList(), duration = 120)

        assertTrue(manifest.contains("<AdaptationSet mimeType=\"video/webm\""))
        assertTrue(manifest.contains("<Representation id=\"v-0\""))
        assertFalse(manifest.contains("<SegmentBase indexRange="))
    }
}
