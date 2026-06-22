package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AllowedPlaylistItem
import dev.typetype.server.routes.adminAllowListRoutes
import dev.typetype.server.services.AdminUserLookupService
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.UserAdminService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val GRANULAR_USER_ID = "granular-user"

class AdminGranularAllowListRoutesTest {
    private val users = UserAdminService()
    private val lookup = AdminUserLookupService()
    private val channels = AllowedChannelsService()
    private val playlists = AllowedPlaylistsService()
    private val settings = SettingsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() { TestDatabase.setup() } }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        insertUser(TEST_USER_ID, "admin", "admin@test.local")
        insertUser(GRANULAR_USER_ID, "user", "family@test.local")
    }

    @Test
    fun `admin user search returns matching users only`() = withApp {
        val body = client.get("/admin/users/search?q=family&limit=10") { authHeader() }.bodyAsText()
        assertTrue(body.contains(GRANULAR_USER_ID))
        assertFalse(body.contains(TEST_USER_ID))
    }

    @Test
    fun `admin user allow list separates global and user scopes`() = withApp {
        settings.upsert(GRANULAR_USER_ID, dev.typetype.server.models.SettingsItem(accessMode = "allow_list"))
        channels.addChannel(TEST_USER_ID, "https://youtube.com/@global", "Global", global = true)
        channels.addChannel(GRANULAR_USER_ID, "https://youtube.com/@user", "User")
        playlists.addPlaylist(TEST_USER_ID, playlist("global"), global = true)
        playlists.addPlaylist(GRANULAR_USER_ID, playlist("user"), global = false)
        val body = client.get("/admin/users/$GRANULAR_USER_ID/allow-list") { authHeader() }.bodyAsText()
        assertTrue(body.contains("\"accessMode\":\"allow_list\""))
        assertTrue(body.contains("\"globalChannels\""))
        assertTrue(body.contains("https://youtube.com/@global"))
        assertTrue(body.contains("https://youtube.com/@user"))
        assertTrue(body.contains("https://youtube.com/playlist?list=global"))
        assertTrue(body.contains("https://youtube.com/playlist?list=user"))
    }

    @Test
    fun `admin can manage user channels and playlists`() = withApp {
        postJson("/admin/users/$GRANULAR_USER_ID/allowed/channels", """{"url":"https://youtube.com/@kid","name":"Kid"}""")
        postJson("/admin/users/$GRANULAR_USER_ID/allowed/playlists", """{"url":"https://youtube.com/playlist?list=kid","title":"Kid"}""")
        var body = client.get("/admin/users/$GRANULAR_USER_ID/allow-list") { authHeader() }.bodyAsText()
        assertTrue(body.contains("https://youtube.com/@kid"))
        assertTrue(body.contains("https://youtube.com/playlist?list=kid"))
        assertEquals(HttpStatusCode.NoContent, client.delete("/admin/users/$GRANULAR_USER_ID/allowed/channels/https%3A%2F%2Fyoutube.com%2F%40kid") { authHeader() }.status)
        assertEquals(HttpStatusCode.NoContent, client.delete("/admin/users/$GRANULAR_USER_ID/allowed/playlists/https%3A%2F%2Fyoutube.com%2Fplaylist%3Flist%3Dkid") { authHeader() }.status)
        body = client.get("/admin/users/$GRANULAR_USER_ID/allow-list") { authHeader() }.bodyAsText()
        assertFalse(body.contains("https://youtube.com/@kid"))
        assertFalse(body.contains("https://youtube.com/playlist?list=kid"))
    }

    @Test
    fun `admin can manage global playlists`() = withApp {
        postJson("/admin/allowed/playlists", """{"url":"https://youtube.com/playlist?list=global","title":"Global"}""")
        assertTrue(client.get("/admin/allowed/playlists") { authHeader() }.bodyAsText().contains("global"))
        val status = client.delete("/admin/allowed/playlists/https%3A%2F%2Fyoutube.com%2Fplaylist%3Flist%3Dglobal") { authHeader() }.status
        assertEquals(HttpStatusCode.NoContent, status)
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { install(ContentNegotiation) { json() }; routing { adminAllowListRoutes(auth, users, lookup, channels, playlists) } }
        block()
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String) = client.post(path) { authHeader(); contentType(ContentType.Application.Json); setBody(body) }
    private fun playlist(id: String) = AllowedPlaylistItem("https://youtube.com/playlist?list=$id", title = id)
    private fun io.ktor.client.request.HttpRequestBuilder.authHeader() { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
}

private fun insertUser(userId: String, role: String, email: String) = transaction {
    UsersTable.insert { it[id] = userId; it[UsersTable.role] = role; it[UsersTable.email] = email; it[passwordHash] = "hash"; it[name] = userId; it[createdAt] = 10L; it[updatedAt] = 10L }
}
