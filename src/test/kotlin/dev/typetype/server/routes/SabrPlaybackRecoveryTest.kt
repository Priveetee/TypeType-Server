package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SabrPlaybackRecoveryTest {
    @Test
    fun `stalled demand requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        every { holder.terminalFailure() } returns "SABR demand stalled for 140:39"
        val recovery = SabrPlaybackRecovery(mockk<SabrSessionStore>())

        assertEquals("retry_fresh_session", recovery.action(holder))
    }
}
