package dev.typetype.server.services

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrPlaybackDeferredPreparationTest {
    @Test
    fun `android preparation can defer initialization and pump startup`() = runTest {
        val audio = mockk<YoutubeSabrFormat> { every { itag } returns 140 }
        val video = mockk<YoutubeSabrFormat> { every { itag } returns 137 }
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true) { every { streamState } returns state }
        val holder = SabrSessionHolder(
            session,
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
        val info = mockk<YoutubeSabrInfo>()
        val prepared = SabrPreparedInfo(info, null)
        val store = mockk<SabrSessionStore> {
            every {
                getOrCreate(
                    "video",
                    "user",
                    info,
                    audio,
                    video,
                    null,
                    0L,
                    false,
                    SabrSessionPurpose.ANDROID_PLAYBACK,
                    false,
                    0L,
                )
            } returns holder
        }

        val result = SabrPlaybackSessionService(store, SabrSessionPurpose.ANDROID_PLAYBACK).prepare(
            "video",
            "user",
            prepared,
            audio,
            video,
            0L,
            preloadInitialization = false,
            startPumpOnPrepare = false,
        )

        assertSame(holder, result.holder)
        coVerify(exactly = 0) { store.fetchInitializationData(any(), any()) }
        verify(exactly = 0) { store.startPump(any()) }
    }
}
