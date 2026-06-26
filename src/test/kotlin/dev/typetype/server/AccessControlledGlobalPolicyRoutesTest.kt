package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistItem
import dev.typetype.server.models.PublicPlaylistResponse
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccessControlledGlobalPolicyRoutesTest {
    private val settings = SettingsService()
    private val allowed = AllowedChannelsService()
    private val allowedPlaylists = AllowedPlaylistsService()
    private val adminSettings = AdminSettingsService()
    private val access = AccessControlService(settings, allowed, allowedPlaylists, adminSettings)
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val playlist: PublicPlaylistService = mockk()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `global allow list filters anonymous public routes to global allowed channels`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(accessMode = "allow_list"))
        allowed.addChannel(TEST_USER_ID, "https://youtube.com/@allowed", "Allowed", global = true)
        coEvery { playlist.getPlaylist(any(), any()) } returns ExtractionResult.Success(
            PublicPlaylistResponse(publicPlaylist(), listOf(video("Allowed", "https://youtube.com/@allowed"), video("Blocked", "https://youtube.com/@blocked")), null)
        )
        application { install(ContentNegotiation) { json() }; routing { publicPlaylistRoutes(playlist, auth, access) } }
        val body = client.get("/playlist?url=https://youtube.com/playlist?list=x").bodyAsText()
        assertTrue(body.contains("\"title\":\"Allowed\""))
        assertFalse(body.contains("\"title\":\"Blocked\""))
    }

    @Test
    fun `global allow list filters guest token routes to global allowed channels`() = testApplication {
        val guestAuth = AuthService("guest-global-policy-test")
        val guestToken = guestAuth.guestLogin()
        adminSettings.upsert(AdminSettingsItem(accessMode = "allow_list", allowGuest = true))
        allowed.addChannel(TEST_USER_ID, "https://youtube.com/@allowed", "Allowed", global = true)
        coEvery { playlist.getPlaylist(any(), any()) } returns ExtractionResult.Success(
            PublicPlaylistResponse(publicPlaylist(), listOf(video("Allowed", "https://youtube.com/@allowed"), video("Blocked", "https://youtube.com/@blocked")), null)
        )
        application { install(ContentNegotiation) { json() }; routing { publicPlaylistRoutes(playlist, guestAuth, access) } }
        val body = client.get("/playlist?url=https://youtube.com/playlist?list=x") {
            headers.append(HttpHeaders.Authorization, "Bearer $guestToken")
        }.bodyAsText()
        assertTrue(body.contains("\"title\":\"Allowed\""))
        assertFalse(body.contains("\"title\":\"Blocked\""))
    }

    private fun video(name: String, url: String) = testVideoItem().copy(title = name, uploaderName = name, uploaderUrl = url)
    private fun publicPlaylist(): PublicPlaylistItem = PublicPlaylistItem("id", "Playlist", "https://youtube.com/playlist?list=x", "", "Owner", 2, "normal")
}
