package dev.typetype.server

import dev.typetype.server.services.AuthService
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AuthServiceDispatcherTest {
    @Test
    fun `slow database work does not block the caller dispatcher`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val callerDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val service = AuthService("test-secret", hasUsersProbe = {
            entered.countDown()
            release.await()
            false
        })

        try {
            runBlocking {
                val pending = async(callerDispatcher) { service.hasUsers() }
                assertTrue(entered.await(2, TimeUnit.SECONDS))

                val result = withTimeout(1_000) {
                    withContext(callerDispatcher) { "responsive" }
                }
                assertEquals("responsive", result)

                release.countDown()
                assertFalse(pending.await())
            }
        } finally {
            release.countDown()
            callerDispatcher.close()
        }
    }
}
