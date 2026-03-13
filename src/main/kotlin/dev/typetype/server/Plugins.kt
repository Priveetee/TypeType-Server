package dev.typetype.server

import dev.typetype.server.models.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

private const val EXTRACTION_RATE_LIMIT = 60
private const val PROXY_RATE_LIMIT = 300
private const val USER_DATA_RATE_LIMIT = 120
private val RATE_LIMIT_WINDOW = 1.minutes

val EXTRACTION_ZONE = RateLimitName("extraction")
val PROXY_ZONE = RateLimitName("proxy")
val USER_DATA_ZONE = RateLimitName("user-data")

fun Application.configurePlugins() {
    val log = LoggerFactory.getLogger("RequestLogger")

    install(CallLogging) {
        format { call ->
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val status = call.response.status()?.value ?: 0
            val displayPath = if (path.startsWith("/proxy")) "$path?url=<masked>" else call.request.uri
            "$method $displayPath -> $status"
        }
    }

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }

    install(Compression) {
        gzip()
    }

    val allowedOrigins = System.getenv("ALLOWED_ORIGINS")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
        .ifEmpty { error("ALLOWED_ORIGINS environment variable must be set") }

    install(CORS) {
        allowOrigins { it in allowedOrigins }
    }

    install(RateLimit) {
        register(EXTRACTION_ZONE) {
            rateLimiter(limit = EXTRACTION_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW)
        }
        register(PROXY_ZONE) {
            rateLimiter(limit = PROXY_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW)
        }
        register(USER_DATA_ZONE) {
            rateLimiter(limit = USER_DATA_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW)
        }
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            log.warn("Bad request: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<Throwable> { call, cause ->
            if (cause is io.ktor.utils.io.ClosedWriteChannelException) return@exception
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            log.error("Unhandled exception on ${call.request.path()}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Internal server error"))
        }
    }
}
