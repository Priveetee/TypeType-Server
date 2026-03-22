package dev.typetype.server

import dev.typetype.server.routes.authRoutes
import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RegistrationSettingsTest {
    private val passwordReset = PasswordResetService()
    private val profile = ProfileService()
    private val adminSettings = AdminSettingsService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    @Test
    fun `register forbidden when disabled and users exist`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowRegistration = false, allowGuest = true, forceEmailVerification = false))
        val auth = AuthService.fixed(TEST_USER_ID, hasUsers = true)
        application {
            install(ContentNegotiation) { json() }
            routing { authRoutes(auth, passwordReset, profile, adminSettings) }
        }
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"new@test.local","password":"secret","name":"New"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `register allowed when disabled but no users exist`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowRegistration = false, allowGuest = true, forceEmailVerification = false))
        val auth = AuthService.fixed(TEST_USER_ID, hasUsers = false)
        application {
            install(ContentNegotiation) { json() }
            routing { authRoutes(auth, passwordReset, profile, adminSettings) }
        }
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"first@test.local","password":"secret","name":"First"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
