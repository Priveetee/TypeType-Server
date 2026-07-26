package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidPlaybackPreparationTest {
    @Test
    fun `one shared preparation publishes the complete manifest`() = runTest {
        val fixture = fixture()
        val calls = mutableListOf<Int>()
        val coordinator = coordinator(fixture, this, sharedInitializer = { _, format ->
            calls += format.itag
            fixture.ready.keys.forEach { fixture.ready[it] = true }
            byteArrayOf(1)
        })

        coordinator.start(fixture.session)
        coordinator.start(fixture.session)
        advanceUntilIdle()

        assertEquals(listOf(137), calls)
        assertInstanceOf(
            AndroidDashManifestResult.Ready::class.java,
            fixture.session.preparation.result(fixture.holder, fixture.manifests),
        )
    }

    @Test
    fun `incomplete shared initialization uses exact fallback`() = runTest {
        val fixture = fixture()
        val sharedCalls = mutableListOf<Int>()
        val exactCalls = mutableListOf<Int>()
        val coordinator = coordinator(
            fixture,
            this,
            sharedInitializer = { _, format ->
                sharedCalls += format.itag
                null
            },
            exactInitializer = { _, format, _ ->
                exactCalls += format.itag
                fixture.ready[format.itag] = true
                byteArrayOf(1)
            },
        )

        coordinator.start(fixture.session)
        advanceUntilIdle()

        assertEquals(listOf(137, 140), sharedCalls)
        assertEquals(listOf(137, 140), exactCalls)
        assertInstanceOf(
            AndroidDashManifestResult.Ready::class.java,
            fixture.session.preparation.result(fixture.holder, fixture.manifests),
        )
    }

    @Test
    fun `shared initialization failure uses exact fallback`() = runTest {
        val fixture = fixture()
        val coordinator = coordinator(
            fixture,
            this,
            sharedInitializer = { _, _ -> throw IOException("SABR bootstrap failed") },
            exactInitializer = { _, format, _ ->
                fixture.ready[format.itag] = true
                byteArrayOf(1)
            },
        )

        coordinator.start(fixture.session)
        advanceUntilIdle()

        assertInstanceOf(
            AndroidDashManifestResult.Ready::class.java,
            fixture.session.preparation.result(fixture.holder, fixture.manifests),
        )
    }

    @Test
    fun `preparation deadline becomes a typed temporary failure`() = runTest {
        val fixture = fixture()
        val coordinator = coordinator(fixture, this, timeoutMs = 100L, sharedInitializer = { _, _ ->
            delay(1_000L)
            byteArrayOf(1)
        })

        coordinator.start(fixture.session)
        advanceUntilIdle()

        val result = fixture.session.preparation.result(fixture.holder, fixture.manifests)
            as AndroidDashManifestResult.TemporaryFailure
        assertEquals("android_playback_preparation_timeout", result.code)
    }

    @Test
    fun `initialization failure becomes a typed temporary failure`() = runTest {
        val fixture = fixture()
        val coordinator = coordinator(
            fixture,
            this,
            sharedInitializer = { _, _ -> null },
            exactInitializer = { _, _, _ -> throw IOException("upstream failed") },
        )

        coordinator.start(fixture.session)
        advanceUntilIdle()

        val result = fixture.session.preparation.result(fixture.holder, fixture.manifests)
            as AndroidDashManifestResult.TemporaryFailure
        assertEquals("android_playback_preparation_failed", result.code)
    }

    @Test
    fun `session cancellation stops preparation work`() = runTest {
        val fixture = fixture()
        val cancelled = AtomicBoolean()
        val coordinator = coordinator(fixture, this, sharedInitializer = { _, _ ->
            try {
                awaitCancellation()
            } finally {
                cancelled.set(true)
            }
        })

        coordinator.start(fixture.session)
        runCurrent()
        fixture.session.cancelPreparation()
        runCurrent()

        assertTrue(cancelled.get())
    }

    private fun coordinator(
        fixture: Fixture,
        scope: CoroutineScope,
        timeoutMs: Long = 1_000L,
        sharedInitializer: suspend (SabrSessionHolder, YoutubeSabrFormat) -> ByteArray? = { _, _ -> null },
        exactInitializer: suspend (SabrSessionHolder, YoutubeSabrFormat, Long) -> ByteArray = { _, _, _ ->
            byteArrayOf(1)
        },
    ): AndroidPlaybackPreparationCoordinator = AndroidPlaybackPreparationCoordinator(
        store = mockk(relaxed = true),
        manifests = fixture.manifests,
        timeoutMs = timeoutMs,
        initializationTimeoutMs = 500L,
        scope = scope,
        sharedInitializer = sharedInitializer,
        exactInitializer = exactInitializer,
    )

    private fun fixture(): Fixture {
        val audio = format(140, true)
        val video = format(137, false)
        val ready = mutableMapOf(140 to false, 137 to false)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true) {
            every { hasSegmentIndex(any()) } answers { ready[firstArg<YoutubeSabrFormat>().itag] == true }
            every { getEndSegment(any()) } returns 3L
            every { getSegmentStartMs(any(), any()) } answers { (secondArg<Int>() - 1L) * 1_000L }
            every { getSegmentEndMs(any(), any()) } answers { secondArg<Int>() * 1_000L }
        }
        val sabr = mockk<YoutubeSabrSession> { every { streamState } returns state }
        val holder = SabrSessionHolder(
            sabr,
            mockk(),
            audio,
            video,
            "android-session",
            SabrSessionKey(
                "video",
                "user",
                140,
                null,
                137,
                0L,
                SabrSessionPurpose.ANDROID_PLAYBACK,
            ),
            Instant.EPOCH,
        )
        return Fixture(
            holder,
            AndroidPlaybackSession(holder, emptyList()),
            AndroidDashManifestService(),
            ready,
        )
    }

    private fun format(itag: Int, audio: Boolean): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { isAudio } returns audio
        every { isVideo } returns !audio
        every { audioTrackId } returns null
        every { mimeType } returns if (audio) {
            "audio/mp4; codecs=\"mp4a.40.2\""
        } else {
            "video/mp4; codecs=\"avc1.640028\""
        }
        every { bitrate } returns if (audio) 128_000 else 2_000_000
        every { width } returns if (audio) 0 else 1920
        every { height } returns if (audio) 0 else 1080
    }

    private data class Fixture(
        val holder: SabrSessionHolder,
        val session: AndroidPlaybackSession,
        val manifests: AndroidDashManifestService,
        val ready: MutableMap<Int, Boolean>,
    )
}
