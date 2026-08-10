package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.OkHttpProxyService
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress

class OkHttpProxyServiceSecurityTest {
    @Test
    fun `arbitrary destinations are rejected without a network call`() = runTest {
        var calls = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                calls += 1
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("private".toResponseBody())
                    .build()
            }
            .build()

        val result = OkHttpProxyService(client).pipe(
            url = "https://example.com/collect",
            rangeHeader = null,
            domandBid = null,
        )

        assertEquals(ExtractionResult.BadRequest("Unsupported proxy host"), result)
        assertEquals(0, calls)
    }

    @Test
    fun `supported media hosts remain available`() = runTest {
        val client = OkHttpClient.Builder()
            .dns(Dns { listOf(InetAddress.getByName("1.1.1.1")) })
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("image".toResponseBody())
                    .build()
            }
            .build()

        val result = OkHttpProxyService(client).pipe(
            url = "https://i.ytimg.com/vi/id/hqdefault.jpg",
            rangeHeader = null,
            domandBid = null,
        )

        assertTrue(result is ExtractionResult.Success)
    }
}
