package dev.typetype.server.services

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class SabrPumpCoordinator {
    private val started = AtomicBoolean(false)
    private val wakeVersion = AtomicLong()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    fun markStarted(): Boolean = started.compareAndSet(false, true)

    fun markStopped(): Unit = started.set(false)

    fun wakeVersion(): Long = wakeVersion.get()

    fun wake(): Unit {
        wakeVersion.incrementAndGet()
        wakeups.trySend(Unit)
    }

    suspend fun awaitWake(observedVersion: Long, timeoutMs: Long): Unit {
        if (timeoutMs <= 0L) return
        if (wakeVersion.get() != observedVersion) return
        withTimeoutOrNull(timeoutMs) {
            while (wakeVersion.get() == observedVersion) wakeups.receive()
        }
    }
}

internal fun SabrSessionHolder.markPumpStarted(): Boolean = pumpCoordinator.markStarted()

internal fun SabrSessionHolder.markPumpStopped(): Unit = pumpCoordinator.markStopped()

internal fun SabrSessionHolder.wakePump(): Unit = pumpCoordinator.wake()

internal fun SabrSessionHolder.pumpWakeVersion(): Long = pumpCoordinator.wakeVersion()

internal suspend fun SabrSessionHolder.awaitPumpWake(observedVersion: Long, timeoutMs: Long): Unit =
    pumpCoordinator.awaitWake(observedVersion, timeoutMs)
