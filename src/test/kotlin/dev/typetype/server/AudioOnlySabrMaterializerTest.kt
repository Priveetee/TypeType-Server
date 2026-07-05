package dev.typetype.server

import dev.typetype.server.routes.materializeSabrAudioOnlyBody
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaHeader
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class AudioOnlySabrMaterializerTest {
    @Test
    fun `materializer writes init and every audio sequence`() = runTest {
        val audio = sabrFormat(140, isAudio = true)
        val video = sabrFormat(137, isAudio = false)
        val holder = holder(audio, video, endSegments = listOf(0L, 0L, 0L, 3L))
        val store = mockk<SabrSessionStore>()
        coEvery { store.fetchSegment(holder, match { it.sequenceNumber == 1 }) } returns segment(140, 1, byteArrayOf(1))
        coEvery { store.fetchSegment(holder, match { it.sequenceNumber == 2 }) } returns segment(140, 2, byteArrayOf(2))
        coEvery { store.fetchSegment(holder, match { it.sequenceNumber == 3 }) } returns segment(140, 3, byteArrayOf(3))

        val body = materializeSabrAudioOnlyBody(store, holder, byteArrayOf(9, 8))

        assertArrayEquals(byteArrayOf(9, 8, 1, 2, 3), body)
    }

    @Test
    fun `materializer fails instead of returning partial body`() = runTest {
        val audio = sabrFormat(140, isAudio = true)
        val video = sabrFormat(137, isAudio = false)
        val holder = holder(audio, video, endSegments = listOf(0L, 0L, 0L))
        val store = mockk<SabrSessionStore>()
        coEvery { store.fetchSegment(holder, match { it.sequenceNumber == 1 }) } returns segment(140, 1, byteArrayOf(1))
        coEvery { store.fetchSegment(holder, match { it.sequenceNumber == 2 }) } returns null

        val body = materializeSabrAudioOnlyBody(store, holder, byteArrayOf(9, 8))

        assertNull(body)
    }

    private fun holder(audio: YoutubeSabrFormat, video: YoutubeSabrFormat, endSegments: List<Long>): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>()
        val streamState = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns streamState
        every { session.isBeyondEnd(any()) } returns false
        every { streamState.setActiveTrackTypes(any(), any()) } returns Unit
        every { streamState.getEndSegment(audio) } returnsMany endSegments
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun sabrFormat(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.isVideo } returns !isAudio
        every { format.audioTrackId } returns null
        return format
    }

    private fun segment(itag: Int, sequence: Int, data: ByteArray): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.isInitSegment } returns false
        every { header.itag } returns itag
        every { header.sequenceNumber } returns sequence
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        every { segment.data } returns data
        return segment
    }

    private val SabrSegmentRequest.sequenceNumber: Int get() = getSequenceNumber()
}
