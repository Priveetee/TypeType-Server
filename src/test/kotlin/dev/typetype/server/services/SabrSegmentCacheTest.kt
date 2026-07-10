package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaHeader
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSegmentCacheTest {
    @Test
    fun `cache stores and reads segment metadata and bytes`() {
        val segmentCache = SabrSegmentCache()
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video)
        val segment = segment(itag = 140, sequence = 4, bytes = byteArrayOf(1, 2, 3))
        val request = SabrSegmentRequest.media(audio, 4)

        segmentCache.put(holder, segment)
        val cached = segmentCache.get(holder, request)

        requireNotNull(cached)
        assertEquals(140, cached.itag)
        assertEquals(4, cached.sequence)
        assertEquals(1234L, cached.startMs)
        assertEquals(9985L, cached.durationMs)
        assertEquals("audio/mp4", cached.mimeType)
        assertArrayEquals(byteArrayOf(1, 2, 3), cached.bytes)
    }

    private fun holder(audio: YoutubeSabrFormat, video: YoutubeSabrFormat): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "token",
            key = SabrSessionKey("video", "user", 140, null, 137, 0L),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.isVideo } returns !isAudio
        every { format.lastModified } returns 7L
        every { format.xtags } returns null
        every { format.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        return format
    }

    private fun segment(itag: Int, sequence: Int, bytes: ByteArray): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.itag } returns itag
        every { header.sequenceNumber } returns sequence
        every { header.isInitSegment } returns false
        every { header.startMs } returns 1234L
        every { header.durationMs } returns 9985L
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        every { segment.data } returns bytes
        every { segment.length } returns bytes.size
        return segment
    }
}
