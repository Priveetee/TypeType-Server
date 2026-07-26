package dev.typetype.server.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal sealed interface AndroidSubtitleInventorySnapshot {
    data object Preparing : AndroidSubtitleInventorySnapshot
    data class Ready(val tracks: List<AndroidSubtitleTrack>) : AndroidSubtitleInventorySnapshot
    data object TemporaryFailure : AndroidSubtitleInventorySnapshot
}

internal class AndroidSubtitleInventoryHandle private constructor(
    initial: AndroidSubtitleInventorySnapshot,
) {
    private val state = AtomicReference(initial)
    private val completed = AtomicBoolean(initial !is AndroidSubtitleInventorySnapshot.Preparing)
    private val completion = CompletableDeferred<AndroidSubtitleInventorySnapshot>().also {
        if (initial !is AndroidSubtitleInventorySnapshot.Preparing) it.complete(initial)
    }

    fun snapshot(): AndroidSubtitleInventorySnapshot = state.get()

    suspend fun await(): AndroidSubtitleInventorySnapshot = completion.await()

    internal fun complete(result: AndroidSubtitleInventorySnapshot) {
        if (!completed.compareAndSet(false, true)) return
        state.set(result)
        completion.complete(result)
    }

    companion object {
        fun preparing(): AndroidSubtitleInventoryHandle =
            AndroidSubtitleInventoryHandle(AndroidSubtitleInventorySnapshot.Preparing)

        fun ready(tracks: List<AndroidSubtitleTrack>): AndroidSubtitleInventoryHandle =
            AndroidSubtitleInventoryHandle(AndroidSubtitleInventorySnapshot.Ready(tracks))

        fun temporaryFailure(): AndroidSubtitleInventoryHandle =
            AndroidSubtitleInventoryHandle(AndroidSubtitleInventorySnapshot.TemporaryFailure)
    }
}

internal class AndroidSubtitleInventoryCoordinator(
    private val service: AndroidSubtitleService,
    private val scope: CoroutineScope,
    private val maxEntries: Int = 256,
    private val cacheTtlMs: Long = 120_000L,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    init {
        require(maxEntries > 0)
        require(cacheTtlMs >= 0L)
    }

    fun start(videoId: String): AndroidSubtitleInventoryHandle {
        var created: AndroidSubtitleInventoryHandle? = null
        val handle = synchronized(lock) {
            cleanupCompleted(nowMs())
            entries[videoId]?.handle ?: allocate(videoId)?.also { created = it }
                ?: AndroidSubtitleInventoryHandle.temporaryFailure()
        }
        created?.let { launchInventory(videoId, it) }
        return handle
    }

    private fun allocate(videoId: String): AndroidSubtitleInventoryHandle? {
        if (entries.size >= maxEntries) {
            entries.entries.firstOrNull { it.value.completedAtMs != null }?.let { entries.remove(it.key) }
        }
        if (entries.size >= maxEntries) return null
        return AndroidSubtitleInventoryHandle.preparing().also { entries[videoId] = Entry(it) }
    }

    private fun launchInventory(videoId: String, handle: AndroidSubtitleInventoryHandle) {
        scope.launch {
            val result = runCatchingNonCancellation { service.inventory(videoId) }
                .getOrDefault(AndroidSubtitleInventoryResult.Unavailable)
                .toSnapshot()
            handle.complete(result)
            synchronized(lock) {
                entries[videoId]?.takeIf { it.handle === handle }?.completedAtMs = nowMs()
            }
        }
    }

    private fun cleanupCompleted(currentMs: Long) {
        entries.entries.removeIf { (_, entry) ->
            entry.completedAtMs?.let { currentMs - it >= cacheTtlMs } == true
        }
    }

    private fun AndroidSubtitleInventoryResult.toSnapshot(): AndroidSubtitleInventorySnapshot = when (this) {
        is AndroidSubtitleInventoryResult.Ready -> AndroidSubtitleInventorySnapshot.Ready(tracks)
        AndroidSubtitleInventoryResult.Unavailable -> AndroidSubtitleInventorySnapshot.TemporaryFailure
    }

    private class Entry(
        val handle: AndroidSubtitleInventoryHandle,
        var completedAtMs: Long? = null,
    )
}
