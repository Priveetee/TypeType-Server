package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSubtitleInventoryCoordinatorTest {
    @Test
    fun `concurrent requests share one in flight inventory extraction`() = runTest {
        val release = CompletableDeferred<Unit>()
        val track = mockk<AndroidSubtitleTrack>()
        val service = mockk<AndroidSubtitleService>()
        coEvery { service.inventory(VIDEO_ID) } coAnswers {
            release.await()
            AndroidSubtitleInventoryResult.Ready(listOf(track))
        }
        val coordinator = AndroidSubtitleInventoryCoordinator(service, this)

        val first = coordinator.start(VIDEO_ID)
        val second = coordinator.start(VIDEO_ID)
        runCurrent()

        assertSame(first, second)
        assertEquals(AndroidSubtitleInventorySnapshot.Preparing, first.snapshot())
        release.complete(Unit)
        val completed = async { first.await() }.await() as AndroidSubtitleInventorySnapshot.Ready
        assertSame(track, completed.tracks.single())
        coVerify(exactly = 1) { service.inventory(VIDEO_ID) }
    }

    @Test
    fun `completed inventory is reused until its cache entry expires`() = runTest {
        var now = 1_000L
        val service = mockk<AndroidSubtitleService>()
        coEvery { service.inventory(VIDEO_ID) } returns AndroidSubtitleInventoryResult.Ready(emptyList())
        val coordinator = AndroidSubtitleInventoryCoordinator(
            service,
            this,
            cacheTtlMs = 500L,
            nowMs = { now },
        )

        val first = coordinator.start(VIDEO_ID)
        runCurrent()
        assertEquals(AndroidSubtitleInventorySnapshot.Ready(emptyList()), first.await())
        assertSame(first, coordinator.start(VIDEO_ID))
        now += 501L
        val refreshed = coordinator.start(VIDEO_ID)
        runCurrent()

        refreshed.await()
        coVerify(exactly = 2) { service.inventory(VIDEO_ID) }
    }

    @Test
    fun `unavailable inventory completes with a typed temporary failure`() = runTest {
        val service = mockk<AndroidSubtitleService>()
        coEvery { service.inventory(VIDEO_ID) } returns AndroidSubtitleInventoryResult.Unavailable
        val coordinator = AndroidSubtitleInventoryCoordinator(service, this)

        val result = coordinator.start(VIDEO_ID)
        runCurrent()

        assertEquals(AndroidSubtitleInventorySnapshot.TemporaryFailure, result.await())
    }

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
    }
}
