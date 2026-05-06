package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.SessionActivityRequest
import dev.typetype.server.routes.adminSessionRoutes
import dev.typetype.server.services.ActiveSessionService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ActiveSessionAuthAndTtlTest {
    private val adminSettings = AdminSettingsService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `admin sessions endpoint is admin only`(): Unit = testApplication {
        insertActiveSessionUser(id = "user-id", role = "user", publicUsername = "plain")
        val auth = AuthService.fixed("user-id")
        application {
            install(ContentNegotiation) { json() }
            routing { adminSessionRoutes(auth, ActiveSessionService(adminSettings)) }
        }
        val response = client.get("/admin/sessions") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `inactive sessions expire after ttl`(): Unit = runBlocking {
        var now = 1_000L
        insertActiveSessionUser(publicUsername = "viewer")
        adminSettings.upsert(AdminSettingsItem(activeSessionsEnabled = true))
        val service = ActiveSessionService(adminSettings) { now }
        service.reportActivity(TEST_USER_ID, SessionActivityRequest(clientName = "web", deviceId = "device-1"), "UA")
        assertEquals(1, service.list().size)
        now += ActiveSessionService.INACTIVITY_TTL_MS + 1
        assertEquals(0, service.list().size)
    }
}
