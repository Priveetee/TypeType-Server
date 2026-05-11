package dev.typetype.server

import com.sun.net.httpserver.HttpServer
import dev.typetype.server.routes.downloaderGatewayRoutes
import dev.typetype.server.services.DownloaderGatewayService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloaderGatewayArtifactProxyTest {
    @Test
    fun `internal artifact redirect streams range response`() = testApplication {
        val requestedRange = AtomicReference<String>()
        val upstream = HttpServer.create(InetSocketAddress(0), 0)
        upstream.createContext("/jobs/test/artifact") { exchange ->
            exchange.responseHeaders.add(HttpHeaders.Location, "http://garage:${upstream.address.port}/object")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        upstream.createContext("/object") { exchange ->
            requestedRange.set(exchange.requestHeaders.getFirst(HttpHeaders.Range))
            val payload = "abc".toByteArray()
            exchange.responseHeaders.add(HttpHeaders.ContentType, "video/mp4")
            exchange.responseHeaders.add(HttpHeaders.ContentDisposition, "inline; filename=demo.mp4")
            exchange.responseHeaders.add(HttpHeaders.AcceptRanges, "bytes")
            exchange.responseHeaders.add(HttpHeaders.ContentRange, "bytes 0-2/6")
            exchange.sendResponseHeaders(206, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        upstream.start()

        val gateway = DownloaderGatewayService(
            baseUrl = "http://127.0.0.1:${upstream.address.port}",
            client = OkHttpClient.Builder().dns(testDns()).followRedirects(false).followSslRedirects(false).build(),
        )

        application {
            routing {
                downloaderGatewayRoutes(gateway)
            }
        }

        try {
            val response = client.get("/downloader/jobs/test/artifact") {
                header(HttpHeaders.Range.lowercase(), "bytes=0-2")
            }
            assertEquals(HttpStatusCode.PartialContent, response.status)
            assertEquals("bytes=0-2", requestedRange.get())
            assertEquals("3", response.headers[HttpHeaders.ContentLength])
            assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
            assertEquals("bytes 0-2/6", response.headers[HttpHeaders.ContentRange])
            assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().startsWith("attachment"))
            assertEquals("abc", response.bodyAsText())
        } finally {
            upstream.stop(0)
        }
    }

    private fun testDns(): Dns = Dns { hostname ->
        if (hostname == "garage") listOf(InetAddress.getByName("127.0.0.1")) else Dns.SYSTEM.lookup(hostname)
    }
}
