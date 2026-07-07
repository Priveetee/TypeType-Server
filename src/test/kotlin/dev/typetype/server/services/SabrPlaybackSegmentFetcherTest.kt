package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaHeader
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrPlaybackSegmentFetcherTest {
    @Test
    fun `missing segment requests refetch behind buffer before retry`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video, minBufferedEndMs = 60_000L, segmentStartMs = 30_000L)
        val expected = mediaSegment(audio, sequence = 4)
        var calls = 0
        val fetcher = testFetcher { _, _ ->
            calls += 1
            if (calls == 1) null else expected
        }

        val fetched = fetcher.fetch(holder, audio, sequence = 4, timeoutMs = 1_000L)

        assertSame(expected, fetched)
        assertEquals(4, holder.consumeRefetch()?.sequenceNumber)
    }

    @Test
    fun `missing segment requests forward seek beyond buffer before retry`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video, minBufferedEndMs = 60_000L, segmentStartMs = 90_000L)
        val expected = mediaSegment(video, sequence = 12)
        var calls = 0
        val fetcher = testFetcher { _, _ ->
            calls += 1
            if (calls == 1) null else expected
        }

        val fetched = fetcher.fetch(holder, video, sequence = 12, timeoutMs = 1_000L)

        assertSame(expected, fetched)
        assertEquals(12, holder.consumeForwardSeek()?.sequenceNumber)
    }

    private fun holder(
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        minBufferedEndMs: Long,
        segmentStartMs: Long,
    ): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(true, true) } returns Unit
        every { state.getMinBufferedEndMs() } returns minBufferedEndMs
        every { state.getSegmentStartMs(any(), any()) } returns segmentStartMs
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", 140, null, 137, 0L),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        return format
    }

    private fun testFetcher(
        fetch: suspend (SabrSessionHolder, org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest) -> SabrMediaSegment?,
    ): SabrPlaybackSegmentFetcher = SabrPlaybackSegmentFetcher(
        fetchSegment = fetch,
        retryDelayMs = 1L,
        refetchAfterMs = 0L,
        recoveryFailureMs = 1_000L,
        forwardSeekAheadMs = 30_000L,
    )

    private fun mediaSegment(format: YoutubeSabrFormat, sequence: Int): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.itag } returns format.itag
        every { header.sequenceNumber } returns sequence
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }
}
