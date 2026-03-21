package dev.typetype.server

import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class RateLimitStreamsZoneTest {

    @Test
    fun `streams zone allows bursts before 429 and includes retry header`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            install(io.ktor.server.plugins.statuspages.StatusPages) {
                status(HttpStatusCode.TooManyRequests) { call, status ->
                    if (!call.response.headers.contains(HttpHeaders.RetryAfter)) call.response.headers.append(HttpHeaders.RetryAfter, "60")
                    call.respondText("{\"error\":\"Too many requests\"}", status = status)
                }
            }
            install(RateLimit) {
                register(STREAMS_ZONE) {
                    rateLimiter(limit = 360, refillPeriod = 1.minutes)
                    requestKey { call -> call.request.local.remoteHost }
                }
            }
            routing {
                rateLimit(STREAMS_ZONE) {
                    get("/streams-sim") { call.respondText("ok") }
                }
            }
        }
        repeat(360) { index ->
            val response = client.get("/streams-sim")
            assertEquals(HttpStatusCode.OK, response.status, "request ${index + 1} should pass")
        }
        val blocked = client.get("/streams-sim")
        assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
        assertNotNull(blocked.headers[HttpHeaders.RetryAfter])
    }
}
