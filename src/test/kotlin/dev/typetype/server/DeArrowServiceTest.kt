package dev.typetype.server

import dev.typetype.server.services.DeArrowRemote
import dev.typetype.server.services.DeArrowService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeArrowServiceTest {
    @Test
    fun `selects accepted title and thumbnail and caches branding`() = runBlocking {
        val remote = FakeDeArrowRemote()
        val service = DeArrowService(FakeCacheService(), remote)
        val first = service.get("stZ3ZoR_8eg")
        val second = service.get("stZ3ZoR_8eg")
        assertEquals("Clear title", first?.title)
        assertEquals("/dearrow/thumbnail?videoId=stZ3ZoR_8eg&time=12.5", first?.thumbnailUrl)
        assertEquals(first, second)
        assertEquals(1, remote.brandingCalls)
    }

    @Test
    fun `rejects invalid video id without remote call`() = runBlocking {
        val remote = FakeDeArrowRemote()
        val service = DeArrowService(FakeCacheService(), remote)
        assertNull(service.get("invalid"))
        assertEquals(0, remote.brandingCalls)
    }
}

private class FakeDeArrowRemote : DeArrowRemote {
    var brandingCalls = 0

    override suspend fun branding(videoId: String): String {
        brandingCalls += 1
        return """{"titles":[{"title":"Clear title","votes":2,"locked":false,"original":false}],"thumbnails":[{"timestamp":12.5,"votes":1,"locked":false,"original":false}],"videoDuration":100,"randomTime":0.4}"""
    }

    override suspend fun thumbnail(videoId: String, timestamp: Double): ByteArray = byteArrayOf(1, 2, 3)
}
