package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.routes.profileRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.ProfileService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProfileInfoRoutesTest {

    private val auth = AuthService.fixed(TEST_USER_ID)
    private val profileService = ProfileService()
    private val avatarService = AvatarService()

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
                it[email] = "profile.info@test.local"
                it[passwordHash] = "hash"
                it[name] = "Profile"
                it[role] = "user"
                it[createdAt] = 0L
                it[updatedAt] = 0L
            }
        }
    }

    @Test
    fun `PUT profile updates publicUsername and bio`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { profileRoutes(profileService, avatarService, auth) }
        }
        val response = client.put("/profile") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"publicUsername":"privee.test","bio":"hello"}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `GET public profile returns only public fields`() = testApplication {
        transaction {
            UsersTable.insert {
                it[id] = "public-user-id"
                it[email] = "public.profile@test.local"
                it[passwordHash] = "hash"
                it[name] = "Public"
                it[role] = "user"
                it[publicUsername] = "public.profile"
                it[bio] = "public bio"
                it[createdAt] = 0L
                it[updatedAt] = 0L
            }
        }
        application {
            install(ContentNegotiation) { json() }
            routing { profileRoutes(profileService, avatarService, auth) }
        }
        val response = client.get("/profile/public/public.profile")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("publicUsername") && body.contains("public bio") && !body.contains("email"))
    }
}
