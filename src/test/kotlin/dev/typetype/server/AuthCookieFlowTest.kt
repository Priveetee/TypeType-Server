package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.routes.authRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import io.ktor.client.request.header
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
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthCookieFlowTest {
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
    fun `login refresh logout flow works with refresh cookie`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowRegistration = true, allowGuest = true, forceEmailVerification = false))
        val auth = AuthService("test-secret")
        auth.register("cookie@test.local", "secret", "Cookie")
        application {
            install(ContentNegotiation) { json() }
            routing { authRoutes(auth, passwordReset, profile, adminSettings) }
        }
        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"identifier":"cookie@test.local","password":"secret"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val setCookie = login.headers.getAll(HttpHeaders.SetCookie).orEmpty().joinToString("; ")
        assertTrue(setCookie.contains("refresh_token="))
        assertTrue(setCookie.contains("Path=/"))
        val refresh = client.post("/auth/refresh") { header(HttpHeaders.Cookie, setCookie) }
        assertEquals(HttpStatusCode.OK, refresh.status)
        assertTrue(refresh.bodyAsText().contains("\"accessToken\":"))
        val logout = client.post("/auth/logout") { header(HttpHeaders.Cookie, setCookie) }
        assertEquals(HttpStatusCode.NoContent, logout.status)
        val refreshAfterLogout = client.post("/auth/refresh") { header(HttpHeaders.Cookie, setCookie) }
        assertEquals(HttpStatusCode.Unauthorized, refreshAfterLogout.status)
    }
}
