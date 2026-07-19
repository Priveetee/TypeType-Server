package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrLiveGapWindowTest {
    @Test
    fun `live gap extends the window from the replacement segment`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { state.isLive } returns true
        every { state.liveHeadTimeMs } returns 510_000L
        every { state.getSegmentStartMs(video, 92) } returns 475_000L
        val holder = holder(session, audio, video)
        holder.setLastServedSequence(video.itag, 91)
        holder.setLastServedSequence(audio.itag, 48)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 299 && request.sequenceNumber == 93 -> cached(299, 93, 480_000L, 5_000L)
                request.format.itag == 299 && request.sequenceNumber == 94 -> cached(299, 94, 485_000L, 5_000L)
                request.format.itag == 140 && request.sequenceNumber == 49 -> cached(140, 49, 479_259L, 9_985L)
                else -> null
            }
        }
        val ranges = listOf(
            SabrPlaybackBufferedRange(video.itag, 450_000L, 475_000L),
            SabrPlaybackBufferedRange(audio.itag, 450_000L, 479_259L),
        )

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 470_000L, 299, 140, bufferGoalMs = 8_000L, bufferedRanges = ranges),
        )

        assertTrue(result.isReady)
        assertEquals(480_000L, result.response.startTimeMs)
        assertEquals(
            listOf(
                "/api/sabr/playback/session/299/segment/93?generation=0",
                "/api/sabr/playback/session/299/segment/94?generation=0",
            ),
            requireNotNull(result.response.video).segments.map { it.url },
        )
    }

    private fun holder(
        session: YoutubeSabrSession,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ): SabrSessionHolder = SabrSessionHolder(
        session = session,
        info = mockk<YoutubeSabrInfo>(),
        audioFormat = audio,
        videoFormat = video,
        sessionToken = "session",
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat = mockk<YoutubeSabrFormat>(relaxed = true) {
        every { this@mockk.itag } returns itag
        every { this@mockk.isAudio } returns isAudio
        every { mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
    }

    private fun cached(itag: Int, sequence: Int, startMs: Long, durationMs: Long): CachedSabrSegment = CachedSabrSegment(
        itag = itag,
        sequence = sequence,
        init = false,
        startMs = startMs,
        durationMs = durationMs,
        mimeType = if (itag == 140) "audio/mp4" else "video/mp4",
        bytesBase64 = "AA==",
        byteLength = 1,
    )
}
