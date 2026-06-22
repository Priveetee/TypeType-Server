package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistItem
import dev.typetype.server.models.PublicPlaylistResponse
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.routes.publicPlaylistRoutes
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicPlaylistService
import dev.typetype.server.services.SettingsService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccessControlledPlaylistAllowRoutesTest {
    private val settings = SettingsService()
    private val channels = AllowedChannelsService()
    private val playlists = AllowedPlaylistsService()
    private val access = AccessControlService(settings, channels, playlists, AdminSettingsService())
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val playlistService: PublicPlaylistService = mockk()

    companion object { @BeforeAll @JvmStatic fun initDb() { TestDatabase.setup() } }

    @BeforeEach fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `allowed playlist keeps videos from non allowed channels`() = testApplication {
        settings.upsert(TEST_USER_ID, SettingsItem(accessMode = "allow_list"))
        playlists.addPlaylist(TEST_USER_ID, dev.typetype.server.models.AllowedPlaylistItem(PLAYLIST_URL), global = false)
        coEvery { playlistService.getPlaylist(any(), any()) } returns ExtractionResult.Success(
            PublicPlaylistResponse(playlist(), listOf(testVideoItem().copy(title = "Kept", uploaderUrl = "https://youtube.com/@blocked")), null)
        )
        application { install(ContentNegotiation) { json() }; routing { publicPlaylistRoutes(playlistService, auth, access) } }
        val body = client.get("/playlist?url=$PLAYLIST_URL") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(body.contains("\"title\":\"Kept\""))
    }

    private fun playlist(): PublicPlaylistItem = PublicPlaylistItem("id", "Allowed", PLAYLIST_URL, "", "Owner", 1, "normal")
}

private const val PLAYLIST_URL = "https://youtube.com/playlist?list=allowed"
