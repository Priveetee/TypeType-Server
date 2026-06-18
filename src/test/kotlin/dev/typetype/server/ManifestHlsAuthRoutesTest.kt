package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.manifestRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.CachedManifestService
import dev.typetype.server.services.CachedNativeManifestService
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.NativeManifestService
import dev.typetype.server.services.SignedHlsManifestCookie
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YoutubeSessionHlsManifestService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManifestHlsAuthRoutesTest {
    private val streamService: StreamService = mockk()
    private val cache = InMemoryHlsAuthCache()
    private val manifestService = CachedManifestService(ManifestService(streamService), cache)
    private val nativeManifestService = CachedNativeManifestService(NativeManifestService(), cache)
    private val hlsManifestService: HlsManifestService = mockk()
    private val youtubeSessionHlsManifestService: YoutubeSessionHlsManifestService = mockk()

    @Test
    fun `GET streams hls-manifest uses signed token`() = testApplication {
        coEvery { youtubeSessionHlsManifestService.hlsManifest("signed") } returns
            ExtractionResult.Success("#EXTM3U")
        installRoutes()
        val response = client.get("/streams/hls-manifest?token=signed")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("#EXTM3U", response.bodyAsText())
    }

    @Test
    fun `GET streams hls-manifest uses bearer session`() = testApplication {
        coEvery {
            youtubeSessionHlsManifestService.hlsManifestForUser(TEST_USER_ID, "https://youtube.com/watch?v=test")
        } returns ExtractionResult.Success("#EXTM3U")
        installRoutes()
        val response = client.get("/streams/hls-manifest?url=https://youtube.com/watch?v=test") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("#EXTM3U", response.bodyAsText())
    }

    @Test
    fun `GET streams hls-manifest uses signed cookie for media request`() = testApplication {
        val url = "https://youtube.com/watch?v=test"
        coEvery { youtubeSessionHlsManifestService.hlsManifest("signed", url) } returns
            ExtractionResult.Success("#EXTM3U")
        installRoutes()
        val response = client.get("/streams/hls-manifest?url=$url") {
            headers.append(HttpHeaders.Cookie, "${SignedHlsManifestCookie.name(url)}=signed")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("#EXTM3U", response.bodyAsText())
    }

    @Test
    fun `GET streams hls-manifest rejects signed token when YouTube Session is unavailable`() = testApplication {
        installRoutes(youtubeSessionHlsManifestService = null)
        val response = client.get("/streams/hls-manifest?token=signed")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertTrue(response.bodyAsText().contains("\"code\":\"youtube_session_unavailable\""))
    }

    private fun ApplicationTestBuilder.installRoutes(
        youtubeSessionHlsManifestService: YoutubeSessionHlsManifestService? = this@ManifestHlsAuthRoutesTest.youtubeSessionHlsManifestService,
    ): Unit {
        application {
            install(ContentNegotiation) { json() }
            routing {
                manifestRoutes(
                    manifestService,
                    nativeManifestService,
                    hlsManifestService,
                    youtubeSessionHlsManifestService,
                    AuthService.fixed(TEST_USER_ID),
                )
            }
        }
    }
}

private class InMemoryHlsAuthCache : CacheService {
    private val map = mutableMapOf<String, String>()
    override suspend fun get(key: String): String? = map[key]
    override suspend fun set(key: String, value: String, ttlSeconds: Long) { map[key] = value }
    override suspend fun delete(key: String) { map.remove(key) }
}
