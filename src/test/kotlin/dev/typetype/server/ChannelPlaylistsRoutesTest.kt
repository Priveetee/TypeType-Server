package dev.typetype.server

import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PlaylistResultItem
import dev.typetype.server.routes.channelRoutes
import dev.typetype.server.services.ChannelService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChannelPlaylistsRoutesTest {
    private val channelService: ChannelService = mockk()

    @Test
    fun `GET channel playlists without url returns 400`() = withApp {
        val response = client.get("/channel/playlists")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET channel playlists returns playlist results`() = withApp {
        coEvery { channelService.getPlaylists(any(), any()) } returns ExtractionResult.Success(response())

        val response = client.get("/channel/playlists?url=https://youtube.com/@test&nextpage=cursor")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"playlists\""))
        assertTrue(body.contains("\"streamCount\":42"))
        coVerify { channelService.getPlaylists("https://youtube.com/@test", "cursor") }
    }

    @Test
    fun `GET channel playlists returns 422 on Failure`() = withApp {
        coEvery { channelService.getPlaylists(any(), any()) } returns ExtractionResult.Failure("error")

        val response = client.get("/channel/playlists?url=https://youtube.com/@test")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    private fun withApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { channelRoutes(channelService) }
        }
        block()
    }

    private fun response() = ChannelPlaylistsResponse(
        playlists = listOf(
            PlaylistResultItem(
                id = "playlist-id",
                title = "Playlist",
                url = "https://youtube.com/playlist?list=playlist-id",
                thumbnailUrl = "https://img.youtube.com/playlist.jpg",
                uploaderName = "Uploader",
                streamCount = 42L,
                playlistType = "normal",
            )
        ),
        nextpage = null,
    )
}
