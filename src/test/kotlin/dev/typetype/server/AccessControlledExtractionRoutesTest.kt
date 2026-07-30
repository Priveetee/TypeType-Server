package dev.typetype.server

import dev.typetype.server.models.ChannelResultItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistItem
import dev.typetype.server.models.PublicPlaylistResponse
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.publicPlaylistRoutes
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicPlaylistService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccessControlledExtractionRoutesTest {
    private val settings = SettingsService()
    private val allowed = AllowedChannelsService()
    private val allowedPlaylists = AllowedPlaylistsService()
    private val adminSettings = AdminSettingsService()
    private val access = AccessControlService(settings, allowed, allowedPlaylists, adminSettings)
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val search: SearchService = mockk()
    private val playlist: PublicPlaylistService = mockk()
    private val streams: StreamService = mockk()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `public filtered routes reject invalid token instead of bypassing allow list`() = testApplication {
        application { install(ContentNegotiation) { json() }; routing { searchRoutes(search, auth, access) } }
        val response = client.get("/search?q=test&service=0") { headers.append(HttpHeaders.Authorization, "Bearer bad") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `search filters videos and channels to allowed channels`() = testApplication {
        enableAllowList()
        coEvery { search.search(any(), any(), any(), any(), any()) } returns ExtractionResult.Success(
            SearchPageResponse(
                items = listOf(video("Allowed", "https://youtube.com/@allowed"), video("Blocked", "https://youtube.com/@blocked")),
                nextpage = null,
                searchSuggestion = null,
                isCorrectedSearch = false,
                channels = listOf(channel("Allowed", "https://youtube.com/@allowed"), channel("Blocked", "https://youtube.com/@blocked")),
            )
        )
        application { install(ContentNegotiation) { json() }; routing { searchRoutes(search, auth, access) } }
        val body = client.get("/search?q=test&service=0") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(body.contains("\"title\":\"Allowed\""))
        assertFalse(body.contains("\"title\":\"Blocked\""))
    }

    @Test
    fun `public playlist filters videos to allowed channels`() = testApplication {
        enableAllowList()
        coEvery { playlist.getPlaylist(any(), any()) } returns ExtractionResult.Success(
            PublicPlaylistResponse(publicPlaylist(), listOf(video("Allowed", "https://youtube.com/@allowed"), video("Blocked", "https://youtube.com/@blocked")), null)
        )
        application { install(ContentNegotiation) { json() }; routing { publicPlaylistRoutes(playlist, auth, access) } }
        val body = client.get("/playlist?url=https://youtube.com/playlist?list=x") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(body.contains("\"title\":\"Allowed\""))
        assertFalse(body.contains("\"title\":\"Blocked\""))
    }

    @Test
    fun `youtube sabr streams block video from non allowed channel`() = testApplication {
        enableAllowList()
        coEvery { streams.getStreamInfo(any()) } returns ExtractionResult.Success(testStreamResponse().copy(uploaderName = "Blocked", uploaderUrl = "https://youtube.com/@blocked"))
        application { install(ContentNegotiation) { json() }; routing { streamRoutes(streams, auth, accessControlService = access) } }
        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=x") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    private suspend fun enableAllowList() {
        settings.upsert(TEST_USER_ID, SettingsItem(accessMode = "allow_list"))
        allowed.addChannel(TEST_USER_ID, "https://youtube.com/@allowed", "Allowed")
    }

    private fun video(name: String, url: String): VideoItem = testVideoItem().copy(title = name, uploaderName = name, uploaderUrl = url)
    private fun channel(name: String, url: String): ChannelResultItem = ChannelResultItem("id-$name", name, url, "", "", 0, 0, false)
    private fun publicPlaylist(): PublicPlaylistItem = PublicPlaylistItem("id", "Playlist", "https://youtube.com/playlist?list=x", "", "Owner", 2, "normal")
}
