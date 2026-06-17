package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.YoutubeSessionCompleteRequest
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YoutubeSessionCompleteResult
import dev.typetype.server.services.YoutubeSessionCrypto
import dev.typetype.server.services.YoutubeSessionService
import dev.typetype.server.services.YoutubeSessionStreamService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class YoutubeSessionStreamServiceTest {
    private val youtubeSessionService = YoutubeSessionService(
        YoutubeSessionCrypto.fromSecret("test-youtube-session-key-32-bytes")
    )

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `non YouTube url does not use authenticated extraction`() = runBlocking {
        val service = YoutubeSessionStreamService(failingStreamService(), youtubeSessionService, FakeCacheService())
        assertNull(service.getStreamInfo(TEST_USER_ID, "https://example.com/watch?v=test"))
    }

    @Test
    fun `rejected authenticated extraction marks session needs reconnect`() = runBlocking {
        connectYoutubeSession()
        val service = YoutubeSessionStreamService(
            failingStreamService("No suitable stream"),
            youtubeSessionService,
            FakeCacheService(),
        )
        val result = service.getStreamInfo(TEST_USER_ID, "https://youtube.com/watch?v=test")
        assertTrue(result is ExtractionResult.BadRequest)
        assertEquals("needs_reconnect", youtubeSessionService.status(TEST_USER_ID).status)
    }

    @Test
    fun `successful authenticated extraction is cached by session`() = runBlocking {
        connectYoutubeSession()
        var calls = 0
        val stream = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                calls += 1
                return ExtractionResult.Success(testStreamResponse())
            }
        }
        val service = YoutubeSessionStreamService(stream, youtubeSessionService, FakeCacheService())
        assertTrue(service.getStreamInfo(TEST_USER_ID, "https://youtube.com/watch?v=test") is ExtractionResult.Success)
        assertTrue(service.getStreamInfo(TEST_USER_ID, "https://youtube.com/watch?v=test") is ExtractionResult.Success)
        assertEquals(1, calls)
    }

    private suspend fun connectYoutubeSession() {
        val pairing = youtubeSessionService.createPairing(TEST_USER_ID)
        val result = youtubeSessionService.complete(
            YoutubeSessionCompleteRequest(
                code = pairing.code,
                cookies = "SID=secret-cookie",
                poToken = "secret-pot-value",
            )
        )
        assertEquals(YoutubeSessionCompleteResult.Completed, result)
    }

    private fun failingStreamService(message: String = "unexpected call"): StreamService = object : StreamService {
        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
            ExtractionResult.Failure(message)
    }
}
