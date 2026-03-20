package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.routes.historyRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HistoryService
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HistoryPaginationRoutesTest {
    private val service = HistoryService()
    private val auth = AuthService.fixed(TEST_USER_ID)

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
    fun `GET history paginates with deterministic ordering and total`() = testApplication {
        application { install(ContentNegotiation) { json() }; routing { historyRoutes(service, auth) } }
        val first = addHistory("alpha", 1000)
        val second = addHistory("beta", 2000)
        val third = addHistory("gamma", 3000)
        val page1 = client.get("/history?limit=2&offset=0") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        val page2 = client.get("/history?limit=2&offset=2") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        val page1Items = Json.decodeFromString<List<HistoryItem>>(page1.bodyAsText())
        val page2Items = Json.decodeFromString<List<HistoryItem>>(page2.bodyAsText())
        assertEquals(HttpStatusCode.OK, page1.status)
        assertEquals("3", page1.headers["X-Total-Count"])
        assertEquals(listOf(third.id, second.id), page1Items.map { it.id })
        assertEquals(listOf(first.id), page2Items.map { it.id })
        assertNotEquals(page1Items.first().id, page2Items.first().id)
    }

    @Test
    fun `GET history supports from and to with q`() = testApplication {
        application { install(ContentNegotiation) { json() }; routing { historyRoutes(service, auth) } }
        addHistory("match early", 1000)
        val mid = addHistory("match window", 2000)
        addHistory("other window", 2100)
        addHistory("match late", 5000)
        val response = client.get("/history?q=match&from=1500&to=3000&limit=40&offset=0") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        val items = Json.decodeFromString<List<HistoryItem>>(response.bodyAsText())
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("1", response.headers["X-Total-Count"])
        assertEquals(listOf(mid.id), items.map { it.id })
    }

    @Test
    fun `GET history rejects invalid from to and offset`() = testApplication {
        application { install(ContentNegotiation) { json() }; routing { historyRoutes(service, auth) } }
        val badFrom = client.get("/history?from=abc") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        val badRange = client.get("/history?from=2000&to=1000") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        val badOffset = client.get("/history?offset=-1") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.BadRequest, badFrom.status)
        assertEquals(HttpStatusCode.BadRequest, badRange.status)
        assertEquals(HttpStatusCode.BadRequest, badOffset.status)
    }

    private suspend fun addHistory(title: String, watchedAt: Long): HistoryItem {
        val item = service.add(TEST_USER_ID, HistoryItem(url = "https://$title.com", title = title, thumbnail = "", channelName = "Channel", channelUrl = "", duration = 10L, progress = 0L))
        DatabaseFactory.query { HistoryTable.update({ HistoryTable.id eq item.id }) { it[HistoryTable.watchedAt] = watchedAt } }
        return item.copy(watchedAt = watchedAt)
    }
}
