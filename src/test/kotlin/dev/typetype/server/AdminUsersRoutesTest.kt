package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.routes.adminRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.UserAdminService
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminUsersRoutesTest {

    private val auth = AuthService.fixed(TEST_USER_ID)
    private val userAdminService = UserAdminService()
    private val passwordResetService = PasswordResetService()
    private val adminSettingsService = AdminSettingsService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        transaction {
            UsersTable.insert {
                it[id] = TEST_USER_ID
                it[email] = "admin@test.local"
                it[passwordHash] = "hash"
                it[name] = "Admin"
                it[role] = "admin"
                it[createdAt] = 10L
                it[updatedAt] = 10L
            }
            repeat(3) { index ->
                UsersTable.insert {
                    it[id] = "user-$index"
                    it[email] = "user$index@test.local"
                    it[passwordHash] = "hash"
                    it[name] = "User$index"
                    it[role] = "user"
                    it[createdAt] = 20L + index
                    it[updatedAt] = 20L + index
                }
            }
        }
    }

    @Test
    fun `GET admin users without pagination returns list`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { adminRoutes(auth, userAdminService, passwordResetService, adminSettingsService) }
        }
        val response = client.get("/admin/users") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().trim().startsWith("["))
    }

    @Test
    fun `GET admin users with page and limit returns paginated payload`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { adminRoutes(auth, userAdminService, passwordResetService, adminSettingsService) }
        }
        val response = client.get("/admin/users?page=1&limit=2") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, root["page"]?.toString()?.toInt())
        assertEquals(2, root["limit"]?.toString()?.toInt())
        assertEquals(4, root["total"]?.toString()?.toLong())
        assertEquals(2, root["items"]?.jsonArray?.size)
    }

    @Test
    fun `GET admin users with invalid page returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { adminRoutes(auth, userAdminService, passwordResetService, adminSettingsService) }
        }
        val response = client.get("/admin/users?page=0&limit=10") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET admin users with invalid limit returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { adminRoutes(auth, userAdminService, passwordResetService, adminSettingsService) }
        }
        val response = client.get("/admin/users?page=1&limit=500") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
