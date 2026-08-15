package dev.typetype.server.services

import dev.typetype.server.downloader.YoutubeAuthUserContext
import org.schabi.newpipe.extractor.ServiceList
import java.util.concurrent.Semaphore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YoutubeSessionTokenScope {
    private const val PUBLIC_PERMITS = 64
    private val permits = Semaphore(PUBLIC_PERMITS, true)

    suspend fun <T> withCredentials(credentials: YoutubeSessionCredentials, block: suspend () -> T): T =
        withPermits(PUBLIC_PERMITS) {
            val youtube = ServiceList.YouTube
            try {
                YoutubeAuthUserContext.set(credentials.authUser)
                youtube.setTokens(credentials.cookies)
                youtube.setAdditionalTokens(credentials.poToken)
                block()
            } finally {
                youtube.setTokens("")
                youtube.setAdditionalTokens("")
                YoutubeAuthUserContext.set(null)
            }
        }

    suspend fun <T> withoutCredentials(block: suspend () -> T): T =
        withPermits(1) {
            val youtube = ServiceList.YouTube
            YoutubeAuthUserContext.set(null)
            youtube.setTokens("")
            youtube.setAdditionalTokens("")
            block()
        }

    private suspend fun <T> withPermits(count: Int, block: suspend () -> T): T {
        withContext(Dispatchers.IO) { permits.acquire(count) }
        return try {
            block()
        } finally {
            permits.release(count)
        }
    }
}
