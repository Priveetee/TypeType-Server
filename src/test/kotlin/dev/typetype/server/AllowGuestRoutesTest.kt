package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.routes.authRoutes
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import dev.typetype.server.services.SearchService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AllowGuestRoutesTest {
    private val adminSettings = AdminSettingsService()
    private val search: SearchService = mockk()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `allowGuest true permits anonymous public extraction`() = testApplication {
        val auth = AuthService("guest-test")
        adminSettings.upsert(AdminSettingsItem(allowGuest = true))
        coEvery { search.search(any(), any(), any(), any(), any()) } returns searchSuccess()
        installSearchApp(auth)

        val response = client.get("/search?q=test&service=0")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `allowGuest false blocks anonymous public extraction`() = testApplication {
        val auth = AuthService("guest-test")
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        installSearchApp(auth)

        val response = client.get("/search?q=test&service=0")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `allowGuest false blocks existing guest token`() = testApplication {
        val auth = AuthService("guest-test")
        val guestToken = auth.guestLogin()
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        installSearchApp(auth)

        val response = client.get("/search?q=test&service=0") {
            headers.append(HttpHeaders.Authorization, "Bearer $guestToken")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `allowGuest false permits authenticated user`() = testApplication {
        val auth = AuthService("guest-test")
        val token = auth.register("user@test.local", "secret", "User").accessToken
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        coEvery { search.search(any(), any(), any(), any(), any()) } returns searchSuccess()
        installSearchApp(auth)

        val response = client.get("/search?q=test&service=0") {
            headers.append(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `allowGuest false disables guest login`() = testApplication {
        val auth = AuthService("guest-test")
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        application {
            install(ContentNegotiation) { json() }
            routing { authRoutes(auth, PasswordResetService(), ProfileService(), adminSettings) }
        }

        val response = client.post("/auth/guest")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun ApplicationTestBuilder.installSearchApp(auth: AuthService): Unit = application {
        install(ContentNegotiation) { json() }
        routing { searchRoutes(search, authService = auth, adminSettingsService = adminSettings) }
    }

    private fun searchSuccess(): ExtractionResult<SearchPageResponse> = ExtractionResult.Success(
        SearchPageResponse(items = emptyList(), nextpage = null, searchSuggestion = null, isCorrectedSearch = false)
    )
}
