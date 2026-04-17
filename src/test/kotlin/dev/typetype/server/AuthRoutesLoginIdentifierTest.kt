package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.routes.authRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthRoutesLoginIdentifierTest {
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
    fun `login accepts public username identifier`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowRegistration = true, allowGuest = true, forceEmailVerification = false))
        val auth = AuthService("test-secret")
        val registerSession = auth.register("identifier@test.local", "secret", "Identifier")
        val userId = auth.verify(registerSession.accessToken) ?: error("missing user id")
        transaction {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[publicUsername] = "InfinityLoop1308"
            }
        }
        application {
            install(ContentNegotiation) { json() }
            routing { authRoutes(auth, passwordReset, profile, adminSettings) }
        }
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"identifier":"InfinityLoop1308","password":"secret"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.bodyAsText().contains("\"accessToken\":"))
    }

    @Test
    fun `login keeps backward compatibility with email field`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowRegistration = true, allowGuest = true, forceEmailVerification = false))
        val auth = AuthService("test-secret")
        auth.register("legacy@test.local", "secret", "Legacy")
        application {
            install(ContentNegotiation) { json() }
            routing { authRoutes(auth, passwordReset, profile, adminSettings) }
        }
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"legacy@test.local","password":"secret"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.bodyAsText().contains("\"accessToken\":"))
    }
}
