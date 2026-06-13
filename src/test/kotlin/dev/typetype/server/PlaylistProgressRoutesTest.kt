package dev.typetype.server

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.routes.playlistRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.ProgressService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlaylistProgressRoutesTest {
    private val playlistService = PlaylistService()
    private val progressService = ProgressService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `GET playlist by id serializes progress fields`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { playlistRoutes(playlistService, auth) }
        }
        val playlist = playlistService.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        playlistService.addVideo(TEST_USER_ID, playlist.id, PlaylistVideoItem(url = "https://yt.com/1", title = "V1", thumbnail = "", duration = 100L))
        progressService.upsert(TEST_USER_ID, "https://yt.com/1", 12L)

        val body = client.get("/playlists/${playlist.id}") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()

        assertTrue(body.contains("\"watchPosition\":12"))
        assertTrue(body.contains("\"watched\":false"))
        assertTrue(body.contains("\"progressUpdatedAt\":"))
    }
}
