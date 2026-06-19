package dev.typetype.server.services

import dev.typetype.server.models.YoutubeRemoteLoginStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class YoutubeRemoteLoginReadinessService(
    private val config: YoutubeRemoteBrowserConfig,
    private val youtubeSessionService: YoutubeSessionService,
    private val client: OkHttpClient = defaultClient(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var cachedStatus: YoutubeRemoteLoginStatus? = null
    private var cachedUntilMs: Long = 0
    private val lock = Any()

    suspend fun status(adminEnabled: Boolean): YoutubeRemoteLoginStatus {
        if (!adminEnabled) return YoutubeRemoteLoginStatus.Disabled
        if (!config.isConfigured || !youtubeSessionService.isConfigured) {
            return YoutubeRemoteLoginStatus.NotConfigured
        }
        return tokenStatus()
    }

    private suspend fun tokenStatus(): YoutubeRemoteLoginStatus {
        val now = nowMs()
        synchronized(lock) {
            val current = cachedStatus
            if (current != null && now < cachedUntilMs) return current
        }
        val fresh = withContext(Dispatchers.IO) { probeToken() }
        synchronized(lock) {
            cachedStatus = fresh
            cachedUntilMs = now + CACHE_TTL_MS
        }
        return fresh
    }

    private fun probeToken(): YoutubeRemoteLoginStatus {
        val request = Request.Builder()
            .url("${config.serviceUrl.trimEnd('/')}/health")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use {
                if (it.isSuccessful) YoutubeRemoteLoginStatus.Ready else YoutubeRemoteLoginStatus.TokenUnreachable
            }
        }.getOrDefault(YoutubeRemoteLoginStatus.TokenUnreachable)
    }

    private companion object {
        const val CACHE_TTL_MS = 30_000L

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .build()
    }
}
