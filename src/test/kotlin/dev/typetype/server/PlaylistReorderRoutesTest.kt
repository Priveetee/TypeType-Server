package dev.typetype.server

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.routes.playlistRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PlaylistService
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlaylistReorderRoutesTest {
    private val service = PlaylistService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `PUT playlist reorder returns 204`() = testApplication {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        service.addVideo(TEST_USER_ID, playlist.id, video("https://yt.com/1"))
        service.addVideo(TEST_USER_ID, playlist.id, video("https://yt.com/2"))
        application { install(ContentNegotiation) { json() }; routing { playlistRoutes(service, auth) } }

        val response = client.put("/playlists/${playlist.id}/reorder") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"order":["https://yt.com/2","https://yt.com/1"]}""")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `PUT playlist reorder returns 400 for invalid order`() = testApplication {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        service.addVideo(TEST_USER_ID, playlist.id, video("https://yt.com/1"))
        application { install(ContentNegotiation) { json() }; routing { playlistRoutes(service, auth) } }

        val response = client.put("/playlists/${playlist.id}/reorder") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"order":["https://yt.com/2"]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private fun video(url: String): PlaylistVideoItem = PlaylistVideoItem(url = url, title = "T", thumbnail = "", duration = 10L)
}
