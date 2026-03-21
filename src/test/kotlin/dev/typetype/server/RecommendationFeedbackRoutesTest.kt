package dev.typetype.server

import dev.typetype.server.routes.recommendationFeedbackRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.RecommendationFeedbackService
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

class RecommendationFeedbackRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val service = RecommendationFeedbackService()

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
    fun `POST and GET recommendation feedback works`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { recommendationFeedbackRoutes(service, auth) }
        }
        val post = client.post("/recommendations/feedback") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"feedbackType":"not_interested","videoUrl":"https://yt.com/v/abc"}""")
        }
        assertEquals(HttpStatusCode.Created, post.status)
        val get = client.get("/recommendations/feedback") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, get.status)
        assertTrue(get.bodyAsText().contains("not_interested"))
    }

    @Test
    fun `POST invalid feedbackType returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { recommendationFeedbackRoutes(service, auth) }
        }
        val response = client.post("/recommendations/feedback") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"feedbackType":"foo"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
