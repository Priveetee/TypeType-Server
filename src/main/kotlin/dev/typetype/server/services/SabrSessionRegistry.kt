package dev.typetype.server.services

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal class SabrSessionRegistry {
    private val sessions = ConcurrentHashMap<SabrSessionKey, SabrSessionHolder>()
    private val sessionsByToken = ConcurrentHashMap<String, SabrSessionHolder>()

    fun get(key: SabrSessionKey): SabrSessionHolder? {
        val holder = sessions[key]
        holder?.touch()
        return holder
    }

    fun put(key: SabrSessionKey, holder: SabrSessionHolder) {
        sessions[key] = holder
        sessionsByToken[holder.sessionToken] = holder
    }

    fun contains(key: SabrSessionKey): Boolean = sessions.containsKey(key)

    fun lookupByItag(videoId: String, userId: String, itag: Int): SabrSessionHolder? {
        val now = Instant.now()
        for ((key, holder) in sessions) {
            if (key.userId != userId) continue
            if (holder.info.videoId != videoId) continue
            if (holder.audioFormat.itag != itag && holder.videoFormat.itag != itag) continue
            holder.touch(now)
            return holder
        }
        return null
    }

    fun lookupByToken(videoId: String, token: String, itag: Int): SabrSessionHolder? {
        val holder = sessionsByToken[token] ?: return null
        if (holder.info.videoId != videoId) return null
        if (holder.audioFormat.itag != itag && holder.videoFormat.itag != itag) return null
        holder.touch()
        return holder
    }

    fun lookupByToken(videoId: String, token: String): SabrSessionHolder? {
        val holder = sessionsByToken[token] ?: return null
        if (holder.info.videoId != videoId) return null
        holder.touch()
        return holder
    }

    fun ensureCapacity(maxSessions: Int) {
        while (sessions.size >= maxSessions) {
            val oldest = sessions.entries
                .filterNot { it.value.hasActiveWebSocket() }
                .minByOrNull { it.value.lastRequestAt }
                ?: return
            remove(oldest.key)
        }
    }

    fun evictIdle(cutoff: Instant) {
        val stale = sessions.entries
            .filter { !it.value.hasActiveWebSocket() && it.value.lastRequestAt.isBefore(cutoff) }
            .map { it.key }
        stale.forEach(::remove)
    }

    fun clear() {
        sessions.clear()
        sessionsByToken.clear()
    }

    private fun remove(key: SabrSessionKey) {
        sessions.remove(key)?.let { holder ->
            sessionsByToken.remove(holder.sessionToken, holder)
        }
    }
}
