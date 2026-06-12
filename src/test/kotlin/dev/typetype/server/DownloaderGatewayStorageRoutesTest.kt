package dev.typetype.server

import com.sun.net.httpserver.HttpServer
import dev.typetype.server.routes.downloaderGatewayRoutes
import dev.typetype.server.services.DownloaderGatewayService
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
import java.net.InetSocketAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloaderGatewayStorageRoutesTest {
    @Test
    fun `job creation relays downloader insufficient storage response`() = testApplication {
        val upstream = HttpServer.create(InetSocketAddress(0), 0)
        upstream.createContext("/jobs") { exchange ->
            val payload = """
                {"code":"insufficient_storage","error":"download storage is below the free-space threshold","freeBytes":1024,"requiredFreeBytes":2048}
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            exchange.sendResponseHeaders(507, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        upstream.start()

        val gateway = DownloaderGatewayService("http://127.0.0.1:${upstream.address.port}")
        application {
            install(ContentNegotiation) { json() }
            routing { downloaderGatewayRoutes(gateway) }
        }

        try {
            val response = client.post("/downloader/jobs") {
                contentType(ContentType.Application.Json)
                setBody("{\"url\":\"https://example.test/video\"}")
            }
            val body = response.bodyAsText()
            assertEquals(HttpStatusCode.fromValue(507), response.status)
            assertTrue(body.contains("\"code\":\"insufficient_storage\""))
            assertTrue(body.contains("Stockage temporairement sature, reessayez plus tard."))
        } finally {
            upstream.stop(0)
        }
    }
}
