package dev.typetype.server

import dev.typetype.server.routes.recommendationEventsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationInterestService
import io.ktor.client.request.get
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

class RecommendationEventsRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val service = RecommendationEventService(RecommendationInterestService())

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
    fun `POST and GET recommendation events works`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { recommendationEventsRoutes(service, auth) }
        }
        val post = client.post("/recommendations/events") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"eventType":"click","videoUrl":"https://yt.com/v/a","title":"linux release"}""")
        }
        assertEquals(HttpStatusCode.Created, post.status)
        val get = client.get("/recommendations/events") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, get.status)
        assertTrue(get.bodyAsText().contains("click"))
    }

    @Test
    fun `POST invalid eventType returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { recommendationEventsRoutes(service, auth) }
        }
        val response = client.post("/recommendations/events") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"eventType":"bad"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST short skip eventType is accepted`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { recommendationEventsRoutes(service, auth) }
        }
        val response = client.post("/recommendations/events") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"eventType":"short_skip","videoUrl":"https://yt.com/v/skip"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }
}
