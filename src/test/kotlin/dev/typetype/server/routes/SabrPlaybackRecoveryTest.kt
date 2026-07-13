package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SabrPlaybackRecoveryTest {
    @Test
    fun `stalled demand invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "SABR demand stalled for 140:39"
        every { holder.key } returns SabrSessionKey("video", "user", 140, "en-US.4", 137, 379_441L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit
        val recovery = SabrPlaybackRecovery(store)

        assertEquals("retry_fresh_session", recovery.action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }
}
