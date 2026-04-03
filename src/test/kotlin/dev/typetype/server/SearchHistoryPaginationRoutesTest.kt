package dev.typetype.server

import dev.typetype.server.routes.searchHistoryRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SearchHistoryService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SearchHistoryPaginationRoutesTest {
    private val service = SearchHistoryService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { searchHistoryRoutes(service, auth) }
        }
        block()
    }

    @Test
    fun `GET search-history supports page and limit`() = withApp {
        service.add(TEST_USER_ID, "term-1")
        service.add(TEST_USER_ID, "term-2")
        service.add(TEST_USER_ID, "term-3")
        service.add(TEST_USER_ID, "term-4")
        service.add(TEST_USER_ID, "term-5")
        service.add(TEST_USER_ID, "term-6")
        val page1 = client.get("/search-history?limit=5&page=1") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        val page2 = client.get("/search-history?limit=5&page=2") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, page1.status)
        assertEquals(HttpStatusCode.OK, page2.status)
        assertEquals("6", page1.headers["X-Total-Count"])
        assertEquals("6", page2.headers["X-Total-Count"])
        assertTrue(page1.bodyAsText().contains("term-6"))
        assertTrue(page2.bodyAsText().contains("term-1"))
    }

    @Test
    fun `GET search-history rejects invalid pagination params`() = withApp {
        val badPage = client.get("/search-history?page=0") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        val badLimit = client.get("/search-history?limit=101") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.BadRequest, badPage.status)
        assertEquals(HttpStatusCode.BadRequest, badLimit.status)
    }
}
