package dev.typetype.server.services

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal class AndroidPlaybackSessionRegistry(
    private val store: SabrSessionStore,
    private val idleTimeout: Duration = Duration.ofMinutes(4),
    private val tombstoneTtl: Duration = Duration.ofMinutes(10),
    private val now: () -> Instant = Instant::now,
) {
    private val sessions = ConcurrentHashMap<String, Lease>()
    private val tombstones = ConcurrentHashMap<String, Instant>()

    fun register(
        holder: SabrSessionHolder,
        subtitles: List<AndroidSubtitleTrack>,
    ): AndroidPlaybackSession {
        val current = now()
        cleanup(current)
        val session = AndroidPlaybackSession(holder, subtitles)
        sessions[holder.sessionToken] = Lease(session, current)
        tombstones.remove(holder.sessionToken)
        return session
    }

    fun lookup(sessionId: String): AndroidPlaybackSessionLookup {
        val current = now()
        cleanup(current)
        val lease = sessions[sessionId]
            ?: return if (tombstones.containsKey(sessionId)) {
                AndroidPlaybackSessionLookup.Expired
            } else {
                AndroidPlaybackSessionLookup.Unknown
            }
        if (!lease.lastAccess.plus(idleTimeout).isAfter(current)) {
            expire(sessionId, lease, current)
            return AndroidPlaybackSessionLookup.Expired
        }
        val holder = store.lookupByToken(sessionId)
        if (holder == null || holder.key.purpose != SabrSessionPurpose.ANDROID_PLAYBACK) {
            expire(sessionId, lease, current)
            return AndroidPlaybackSessionLookup.Expired
        }
        lease.lastAccess = current
        return AndroidPlaybackSessionLookup.Active(AndroidPlaybackSession(holder, lease.session.subtitles))
    }

    internal fun expire(sessionId: String): Unit {
        val current = now()
        sessions[sessionId]?.let { expire(sessionId, it, current) }
        cleanup(current)
    }

    private fun expire(sessionId: String, lease: Lease, current: Instant): Unit {
        if (!sessions.remove(sessionId, lease)) return
        tombstones[sessionId] = current.plus(tombstoneTtl)
        store.release(lease.session.holder)
    }

    private fun cleanup(current: Instant): Unit {
        sessions.entries
            .filter { !it.value.lastAccess.plus(idleTimeout).isAfter(current) }
            .forEach { expire(it.key, it.value, current) }
        tombstones.entries.removeIf { !it.value.isAfter(current) }
        if (tombstones.size <= MAX_TOMBSTONES) return
        tombstones.entries.sortedBy { it.value }
            .take(tombstones.size - MAX_TOMBSTONES)
            .forEach { tombstones.remove(it.key, it.value) }
    }

    private class Lease(val session: AndroidPlaybackSession, @Volatile var lastAccess: Instant)

    private companion object {
        const val MAX_TOMBSTONES = 2_048
    }
}

internal data class AndroidPlaybackSession(
    val holder: SabrSessionHolder,
    val subtitles: List<AndroidSubtitleTrack>,
)

internal sealed interface AndroidPlaybackSessionLookup {
    data class Active(val session: AndroidPlaybackSession) : AndroidPlaybackSessionLookup
    data object Expired : AndroidPlaybackSessionLookup
    data object Unknown : AndroidPlaybackSessionLookup
}
