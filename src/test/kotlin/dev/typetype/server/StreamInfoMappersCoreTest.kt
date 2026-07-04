package dev.typetype.server

import dev.typetype.server.services.toAudioStreamItem
import dev.typetype.server.services.toVideoStreamItem
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

class StreamInfoMappersCoreTest {
    @Test
    fun `video and audio mappings keep safe defaults`() {
        val video = mockk<VideoStream>()
        every { video.getContent() } returns null
        every { video.isUrl } returns true
        every { video.getFormat() } returns null
        every { video.getResolution() } returns "1080p"
        every { video.getBitrate() } returns 0
        every { video.getCodec() } returns "avc1.640028"
        every { video.getItag() } returns 137
        every { video.getWidth() } returns 1920
        every { video.getHeight() } returns 1080
        every { video.getFps() } returns 30
        every { video.getItagItem() } returns null
        every { video.getInitStart() } returns 0
        every { video.getInitEnd() } returns 0
        every { video.getIndexStart() } returns 0
        every { video.getIndexEnd() } returns 0
        every { video.getDeliveryMethod() } returns DeliveryMethod.PROGRESSIVE_HTTP
        val mappedVideo = video.toVideoStreamItem("dQw4w9WgXcQ", isVideoOnly = true)
        assertEquals("", mappedVideo.url)
        assertEquals("", mappedVideo.mimeType)
        assertNull(mappedVideo.bitrate)
        assertEquals("avc1.640028", mappedVideo.codec)

        val audio = mockk<AudioStream>()
        every { audio.getContent() } returns null
        every { audio.isUrl } returns true
        every { audio.getFormat() } returns null
        every { audio.averageBitrate } returns 0
        every { audio.getCodec() } returns ""
        every { audio.getQuality() } returns null
        every { audio.getItag() } returns 140
        every { audio.getItagItem() } returns null
        every { audio.getInitStart() } returns 0
        every { audio.getInitEnd() } returns 0
        every { audio.getIndexStart() } returns 0
        every { audio.getIndexEnd() } returns 0
        every { audio.getAudioTrackId() } returns "en.0"
        every { audio.getAudioTrackName() } returns "English"
        every { audio.getAudioLocale() } returns "en"
        every { audio.getDeliveryMethod() } returns DeliveryMethod.PROGRESSIVE_HTTP
        val mappedAudio = audio.toAudioStreamItem("dQw4w9WgXcQ")
        assertEquals("", mappedAudio.url)
        assertEquals("", mappedAudio.mimeType)
        assertNull(mappedAudio.bitrate)
        assertNull(mappedAudio.codec)
        assertEquals("en", mappedAudio.audioLocale)
        assertEquals(false, mappedAudio.isOriginal)
    }

    @Test
    fun `sabr mappings expose manifest but not media url`() {
        val video = mockk<VideoStream>()
        every { video.getContent() } returns "https://example.invalid/sabr-video"
        every { video.isUrl } returns true
        every { video.getFormat() } returns null
        every { video.getResolution() } returns "1080p"
        every { video.getBitrate() } returns 1200
        every { video.getCodec() } returns "avc1.640028"
        every { video.getItag() } returns 137
        every { video.getWidth() } returns 1920
        every { video.getHeight() } returns 1080
        every { video.getFps() } returns 30
        every { video.getItagItem() } returns null
        every { video.getInitStart() } returns 0
        every { video.getInitEnd() } returns 0
        every { video.getIndexStart() } returns 0
        every { video.getIndexEnd() } returns 0
        every { video.getDeliveryMethod() } returns DeliveryMethod.SABR

        val mappedVideo = video.toVideoStreamItem("dQw4w9WgXcQ", isVideoOnly = true)
        assertEquals("", mappedVideo.url)
        assertEquals("sabr", mappedVideo.deliveryMethod)
        assertNull(mappedVideo.manifestUrl)
        assertEquals("/sabr/session/dQw4w9WgXcQ?videoItag=137", mappedVideo.sabrSessionUrl)

        val audio = mockk<AudioStream>()
        every { audio.getContent() } returns "https://example.invalid/sabr-audio"
        every { audio.isUrl } returns true
        every { audio.getFormat() } returns null
        every { audio.averageBitrate } returns 160
        every { audio.getCodec() } returns "mp4a.40.2"
        every { audio.getQuality() } returns "tiny"
        every { audio.getItag() } returns 140
        every { audio.getItagItem() } returns null
        every { audio.getInitStart() } returns 0
        every { audio.getInitEnd() } returns 0
        every { audio.getIndexStart() } returns 0
        every { audio.getIndexEnd() } returns 0
        every { audio.getAudioTrackId() } returns "en.0"
        every { audio.getAudioTrackName() } returns null
        every { audio.getAudioLocale() } returns null
        every { audio.getDeliveryMethod() } returns DeliveryMethod.SABR

        val mappedAudio = audio.toAudioStreamItem("dQw4w9WgXcQ")
        assertEquals("", mappedAudio.url)
        assertEquals("sabr", mappedAudio.deliveryMethod)
        assertNull(mappedAudio.manifestUrl)
        assertEquals("/sabr/session/dQw4w9WgXcQ?audioItag=140&audioTrackId=en.0", mappedAudio.sabrSessionUrl)
    }
}
