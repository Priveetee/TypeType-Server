package dev.typetype.server.services

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class PipePipeInterruptibleTest {
    @Test
    fun `cancelling extraction interrupts blocking pipepipe call`() = runBlocking {
        val started = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val job = launch {
            runPipePipeCall {
                started.countDown()
                try {
                    Thread.sleep(30_000L)
                } catch (error: InterruptedException) {
                    interrupted.set(true)
                    throw error
                }
            }
        }

        withTimeout(2_000L) {
            while (started.count > 0L) delay(10L)
        }
        job.cancelAndJoin()

        assertTrue(interrupted.get())
    }
}
