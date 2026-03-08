package dev.typetype.server

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.routes.playlistRoutes
import dev.typetype.server.services.PlaylistService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
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
import org.junit.jupiter.api.Test

class PlaylistRoutesTest {

    private val service: PlaylistService = mockk()
    private val token = "test-token"

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { playlistRoutes(service, token) }
        }
        block()
    }

    private fun testPlaylist() = PlaylistItem(id = "1", name = "My Playlist")
    private fun testVideo() = PlaylistVideoItem(url = "https://yt.com", title = "T", thumbnail = "", duration = 100L)
    private val playlistBody = """{"name":"My Playlist"}"""
    private val videoBody = """{"url":"https://yt.com","title":"T","thumbnail":"","duration":100}"""

    @Test
    fun `GET playlists without token returns 401`() = withApp {
        val response = client.get("/playlists")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET playlists returns 200`() = withApp {
        coEvery { service.getAll() } returns emptyList()
        val response = client.get("/playlists") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST playlists returns 201`() = withApp {
        coEvery { service.create(any()) } returns testPlaylist()
        val response = client.post("/playlists") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(playlistBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `GET playlists by id returns 200 when found`() = withApp {
        coEvery { service.getById("1") } returns testPlaylist()
        val response = client.get("/playlists/1") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET playlists by id returns 404 when not found`() = withApp {
        coEvery { service.getById("1") } returns null
        val response = client.get("/playlists/1") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT playlists returns 204 when found`() = withApp {
        coEvery { service.update("1", any()) } returns true
        val response = client.put("/playlists/1") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(playlistBody)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `DELETE playlists returns 204 when found`() = withApp {
        coEvery { service.delete("1") } returns true
        val response = client.delete("/playlists/1") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `POST playlists videos returns 201`() = withApp {
        coEvery { service.addVideo("1", any<PlaylistVideoItem>()) } returns testVideo()
        val response = client.post("/playlists/1/videos") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(videoBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `DELETE playlists video returns 204 when found`() = withApp {
        coEvery { service.removeVideo("1", any()) } returns true
        val response = client.delete("/playlists/1/videos/https%3A%2F%2Fyt.com") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
