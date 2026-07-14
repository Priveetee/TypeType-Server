package dev.typetype.server

import dev.typetype.server.AdminUsersRoutesTestFixture.seedUsers
import dev.typetype.server.AdminUsersRoutesTestFixture.withApp
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminUsersRoutesTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        seedUsers()
    }

    @Test
    fun `GET admin users without pagination returns list`() = withApp {
        val response = client.get("/admin/users") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().trim().startsWith("["))
    }

    @Test
    fun `GET admin users with page and limit returns paginated payload`() = withApp {
        val response = client.get("/admin/users?page=1&limit=2") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, root["page"]?.toString()?.toInt())
        assertEquals(2, root["limit"]?.toString()?.toInt())
        assertEquals(4, root["total"]?.toString()?.toLong())
        assertEquals(2, root["items"]?.jsonArray?.size)
    }

    @Test
    fun `GET admin users with invalid page returns 400`() = withApp {
        val response = client.get("/admin/users?page=0&limit=10") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET admin users with invalid limit returns 400`() = withApp {
        val response = client.get("/admin/users?page=1&limit=500") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT admin role rejects self role change with 403`() = withApp {
        val response = client.put("/admin/users/$TEST_USER_ID/role") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"role":"moderator"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("Cannot modify your own role"))
    }

    @Test
    fun `POST admin suspend rejects self suspend with 403`() = withApp {
        val response = client.post("/admin/users/$TEST_USER_ID/suspend") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("Cannot suspend your own account"))
    }

    @Test
    fun `admin can suspend and unsuspend another user`() = withApp {
        val suspendResponse = client.post("/admin/users/user-0/suspend") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        val unsuspendResponse = client.delete("/admin/users/user-0/suspend") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.NoContent, suspendResponse.status)
        assertEquals(HttpStatusCode.NoContent, unsuspendResponse.status)
    }
}
