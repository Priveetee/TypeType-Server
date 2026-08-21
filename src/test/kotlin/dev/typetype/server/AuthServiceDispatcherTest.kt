package dev.typetype.server

import dev.typetype.server.services.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlin.math.max

class AuthServiceDispatcherTest {
    @Test
    fun `database work remains available when shared IO is saturated`() {
        val ioParallelism = max(64, Runtime.getRuntime().availableProcessors())
        val entered = CountDownLatch(ioParallelism)
        val release = CountDownLatch(1)
        val service = AuthService("test-secret", hasUsersProbe = { true })

        runBlocking {
            val blockers = List(ioParallelism) {
                async(Dispatchers.IO) {
                    entered.countDown()
                    release.await()
                }
            }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(withTimeout(1_000) { service.hasUsers() })
            } finally {
                release.countDown()
                blockers.awaitAll()
            }
        }
    }

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
