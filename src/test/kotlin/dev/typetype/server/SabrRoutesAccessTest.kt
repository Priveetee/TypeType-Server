package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.routes.sabrRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SabrRoutesAccessTest {
    private val adminSettings = AdminSettingsService()
    private val streamService: StreamService = mockk(relaxed = true)
    private val sabrSessionStore: SabrSessionStore = mockk(relaxed = true)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `manifest rejects anonymous when guests are disabled`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        installSabrApp()

        val response = client.get("/sabr/manifest/dqWhXeGQkgU")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `segment without session rejects anonymous when guests are disabled`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        installSabrApp()

        val response = client.get("/sabr/dqWhXeGQkgU/140/init")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun ApplicationTestBuilder.installSabrApp(): Unit = application {
        install(ContentNegotiation) { json() }
        install(WebSockets)
        routing {
            sabrRoutes(
                sabrSessionStore,
                streamService,
                AuthService("sabr-test-secret"),
                accessControlService = null,
                adminSettingsService = adminSettings,
                audioOnlyTokenService = null,
            )
        }
    }
}
