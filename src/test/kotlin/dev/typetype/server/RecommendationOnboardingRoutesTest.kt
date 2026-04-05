package dev.typetype.server

import dev.typetype.server.routes.recommendationOnboardingRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.RecommendationOnboardingService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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

class RecommendationOnboardingRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val service = RecommendationOnboardingService()

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
    fun `topics endpoint returns onboarding catalog`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { recommendationOnboardingRoutes(service, auth) }
        }
        val response = client.get("/recommendations/onboarding/topics") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("minTopics"))
        assertTrue(response.bodyAsText().contains("Gaming"))
    }

    @Test
    fun `cannot complete onboarding before minimum topics`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { recommendationOnboardingRoutes(service, auth) }
        }
        val response = client.post("/recommendations/onboarding/complete") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `save preferences then complete onboarding works`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { recommendationOnboardingRoutes(service, auth) }
        }
        val save = client.put("/recommendations/onboarding/preferences") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"selectedTopics":["Linux","GTA","Hardware"],"selectedChannels":["https://www.youtube.com/channel/test"]}""")
        }
        assertEquals(HttpStatusCode.OK, save.status)
        val complete = client.post("/recommendations/onboarding/complete") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, complete.status)
        assertTrue(complete.bodyAsText().contains("\"requiresOnboarding\":false"))
    }
}
