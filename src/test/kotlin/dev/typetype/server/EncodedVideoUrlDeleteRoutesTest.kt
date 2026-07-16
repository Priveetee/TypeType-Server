package dev.typetype.server

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.routes.favoritesRoutes
import dev.typetype.server.routes.playlistRoutes
import dev.typetype.server.routes.watchLaterRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.WatchLaterService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EncodedVideoUrlDeleteRoutesTest {

    private val auth = AuthService.fixed(TEST_USER_ID)
    private val favoritesService = FavoritesService()
    private val playlistService = PlaylistService()
    private val watchLaterService = WatchLaterService()

    companion object {
        private const val VIDEO_URL = "https://www.youtube.com/watch?v=ZZZZZZZZZZZ"
        private const val ENCODED_VIDEO_URL = "https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DZZZZZZZZZZZ"

        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                favoritesRoutes(favoritesService, auth)
                playlistRoutes(playlistService, auth)
                watchLaterRoutes(watchLaterService, auth)
            }
        }
        block()
    }

    @Test
    fun `DELETE favorites removes encoded youtube watch url added by POST`() = withApp {
        val post = client.post("/favorites/$ENCODED_VIDEO_URL") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.Created, post.status)
        assertTrue(post.bodyAsText().contains("\"videoUrl\":\"$VIDEO_URL\""))

        val delete = client.delete("/favorites/$ENCODED_VIDEO_URL") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.NoContent, delete.status)
    }

    @Test
    fun `DELETE playlists video removes encoded youtube watch url added by POST`() = withApp {
        val playlist = playlistService.create(TEST_USER_ID, PlaylistItem(name = "Favorites"))
        val body = """{"url":"$VIDEO_URL","title":"test","thumbnail":"","duration":0}"""
        val post = client.post("/playlists/${playlist.id}/videos") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, post.status)

        val delete = client.delete("/playlists/${playlist.id}/videos/$ENCODED_VIDEO_URL") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.NoContent, delete.status)
        val afterDelete = client.get("/playlists/${playlist.id}") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertFalse(afterDelete.contains(VIDEO_URL))
    }

    @Test
    fun `DELETE watch later removes encoded youtube watch url`() = withApp {
        watchLaterService.add(TEST_USER_ID, WatchLaterItem(url = VIDEO_URL, title = "test", thumbnail = "", duration = 0L))

        val delete = client.delete("/watch-later/$ENCODED_VIDEO_URL") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.NoContent, delete.status)

        val afterDelete = client.get("/watch-later") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertFalse(afterDelete.contains(VIDEO_URL))
    }
}
