package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaHeader
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrLivePlaybackTest {
    @AfterEach
    fun clearDemands(): Unit = SabrSegmentDemandTracker.clearAll()

    @Test
    fun `active live resolves zero start behind the observed head`() {
        val fixture = fixture()

        val live = requireNotNull(fixture.holder.livePlaybackSnapshot())

        assertTrue(live.active)
        assertFalse(live.postLiveDvr)
        assertEquals(1_005_000L, live.seekableEndMs)
        assertEquals(985_000L, fixture.holder.resolvePlaybackStartMs(0L))
        assertEquals(1_005_000L, fixture.holder.resolvePlaybackStartMs(1_100_000L))
    }

    @Test
    fun `reported live head wins over sequence based duration estimates`() {
        val fixture = fixture()
        every { fixture.state.getSegmentEndMs(fixture.video, 200) } returns 9_011_868_000L
        every { fixture.state.getBufferedEndMs(fixture.video) } returns 9_011_868_000L

        val live = requireNotNull(fixture.holder.livePlaybackSnapshot())

        assertEquals(1_005_000L, live.headTimeMs)
        assertEquals(1_005_000L, live.seekableEndMs)
    }

    @Test
    fun `active live maps time from an observed sabr segment for every codec`() {
        val fixture = fixture()
        val header = mockk<SabrMediaHeader> {
            every { isInitSegment } returns false
            every { itag } returns fixture.video.itag
            every { sequenceNumber } returns 180
            every { startMs } returns 965_000L
            every { durationMs } returns 2_000L
        }
        val segment = mockk<SabrMediaSegment> {
            every { this@mockk.header } returns header
        }
        fixture.holder.observeMediaSegment(segment)

        assertEquals(200L, requireNotNull(fixture.holder.livePlaybackSnapshot()).headSequence)
        assertEquals(segment, fixture.holder.observedMediaSegment(fixture.video))
        assertEquals(965_000L, segment.header.startMs)
        assertEquals(195, fixture.holder.playbackStartSequence(fixture.video, 995_000L))
        assertEquals(195, fixture.holder.playbackStartSequence(fixture.video, 995_001L))
        assertEquals(180, fixture.holder.playbackStartSequence(fixture.video, 965_000L))
        assertEquals(179, fixture.holder.playbackStartSequence(fixture.video, 964_999L))
        assertEquals(200, fixture.holder.playbackStartSequence(fixture.video, 1_006_000L))
    }

    @Test
    fun `active live derives missing segment duration from the sabr head`() {
        val fixture = fixture()
        val header = mockk<SabrMediaHeader> {
            every { isInitSegment } returns false
            every { itag } returns fixture.video.itag
            every { sequenceNumber } returns 180
            every { startMs } returns 965_000L
            every { durationMs } returns -1L
        }
        val segment = mockk<SabrMediaSegment> {
            every { this@mockk.header } returns header
        }
        fixture.holder.observeMediaSegment(segment)

        assertEquals(195, fixture.holder.playbackStartSequence(fixture.video, 995_000L))
        assertEquals(180, fixture.holder.playbackStartSequence(fixture.video, 965_000L))
    }

    @Test
    fun `live duration tolerates millisecond drift between media and head timestamps`() {
        val fixture = fixture()
        every { fixture.state.liveHeadTimeMs } returns 1_004_999L
        val header = mockk<SabrMediaHeader> {
            every { isInitSegment } returns false
            every { itag } returns fixture.video.itag
            every { sequenceNumber } returns 180
            every { startMs } returns 965_000L
            every { durationMs } returns -1L
        }
        fixture.holder.observeMediaSegment(mockk { every { this@mockk.header } returns header })

        assertEquals(2_000L, fixture.holder.playbackSegmentDurationMs(fixture.video, 180))
        assertEquals(195, fixture.holder.playbackStartSequence(fixture.video, 995_000L))
    }

    @Test
    fun `only the next live media segments wait for production`() {
        val fixture = fixture()

        assertTrue(fixture.holder.isFutureLiveRequest(SabrSegmentRequest.media(fixture.video, 201)))
        assertTrue(fixture.holder.isFutureLiveRequest(SabrSegmentRequest.media(fixture.video, 202)))
        assertTrue(fixture.holder.isFutureLiveRequest(SabrSegmentRequest.media(fixture.audio, 101)))
        assertTrue(fixture.holder.isFutureLiveRequest(SabrSegmentRequest.media(fixture.audio, 102)))
        assertFalse(fixture.holder.isFutureLiveRequest(SabrSegmentRequest.media(fixture.video, 203)))
        assertFalse(fixture.holder.isFutureLiveRequest(SabrSegmentRequest.media(fixture.video, 200)))
        assertFalse(fixture.holder.isFutureLiveRequest(SabrSegmentRequest.media(fixture.audio, 103)))
        assertFalse(fixture.holder.isFutureLiveRequest(SabrSegmentRequest.media(fixture.audio, 100)))
    }

    @Test
    fun `live retries immediately behind the head and paces future media`() {
        val fixture = fixture()
        val available = SabrSegmentRequest.media(fixture.video, 200)
        val future = SabrSegmentRequest.media(fixture.video, 201)

        assertEquals(DEFAULT_PLAYBACK_RETRY_MS, fixture.holder.liveRetryAfterMs(listOf(available)))
        assertEquals(LIVE_EDGE_POLL_MS, fixture.holder.liveRetryAfterMs(listOf(future)))
    }

    @Test
    fun `future live demand remains retryable after repeated responses`() {
        val fixture = fixture()
        val request = SabrSegmentRequest.media(fixture.video, 201)
        fixture.holder.requestSegmentDemand(request, registeredAtMs = 0L)
        val identity = requireNotNull(fixture.holder.segmentDemandIdentity(request))
        val result = mockk<YoutubeSabrSession.DemandResponseResult> {
            every { segmentCount } returns 2
            every { targetTrackSegmentCount } returns 1
        }
        val runtime = SabrPumpRuntime { 20_000L }
        val wasFutureLiveRequest = fixture.holder.isFutureLiveRequest(request)
        every { fixture.state.getMaxSegment(fixture.video) } returns 201

        repeat(4) {
            assertFalse(
                SabrDemandAttemptFinisher.finish(
                    fixture.holder,
                    request,
                    identity,
                    result,
                    runtime,
                    wasFutureLiveRequest,
                ),
            )
        }

        assertEquals(SabrPlaybackState.WAITING_FOR_LIVE, fixture.holder.playbackState())
        assertNull(fixture.holder.terminalFailure())
        assertEquals("299:201", fixture.holder.pendingSegmentDemandSummary())
    }

    @Test
    fun `post live dvr is finite instead of an active live edge`() {
        val fixture = fixture(postLiveDvr = true)

        val live = requireNotNull(fixture.holder.livePlaybackSnapshot())

        assertFalse(live.active)
        assertTrue(live.postLiveDvr)
        assertFalse(fixture.holder.isFutureLiveRequest(SabrSegmentRequest.media(fixture.video, 201)))
    }

    private fun fixture(postLiveDvr: Boolean = false): Fixture {
        val audio = format(140, true)
        val video = format(299, false)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns !postLiveDvr
        every { session.isAtLiveEdge } returns !postLiveDvr
        every { session.liveHeadSequenceNumber } returns 200L
        every { session.getCachedSegment(any()) } returns null
        every { state.isLive } returns !postLiveDvr
        every { state.isPostLiveDvr } returns postLiveDvr
        every { state.liveHeadSequenceNumber } returns 200L
        every { state.liveHeadTimeMs } returns 1_005_000L
        every { state.getMaxSegment(audio) } returns 100
        every { state.getMaxSegment(video) } returns 200
        every { state.getSegmentEndMs(audio, 100) } returns 1_000_000L
        every { state.getSegmentEndMs(video, 200) } returns 1_002_000L
        every { state.getBufferedEndMs(audio) } returns 1_000_000L
        every { state.getBufferedEndMs(video) } returns 1_002_000L
        every { state.getMinBufferedEndMs() } returns 1_000_000L
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
        return Fixture(holder, session, state, audio, video)
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        return format
    }

    private data class Fixture(
        val holder: SabrSessionHolder,
        val session: YoutubeSabrSession,
        val state: YoutubeSabrStreamState,
        val audio: YoutubeSabrFormat,
        val video: YoutubeSabrFormat,
    )
}
