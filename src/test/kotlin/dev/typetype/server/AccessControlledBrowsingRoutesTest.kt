package dev.typetype.server

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.channelRoutes
import dev.typetype.server.routes.trendingRoutes
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.TrendingService
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
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccessControlledBrowsingRoutesTest {
    private val settings = SettingsService()
    private val allowed = AllowedChannelsService()
    private val adminSettings = AdminSettingsService()
    private val access = AccessControlService(settings, allowed, adminSettings)
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val trending: TrendingService = mockk()
    private val channel: ChannelService = mockk()

    companion object { @BeforeAll @JvmStatic fun initDb() { TestDatabase.setup() } }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `trending filters videos to allowed channels`() = testApplication {
        enableAllowList()
        coEvery { trending.getTrending(any()) } returns ExtractionResult.Success(
            listOf(video("Allowed", "https://youtube.com/@allowed"), video("Blocked", "https://youtube.com/@blocked"))
        )
        application { install(ContentNegotiation) { json() }; routing { trendingRoutes(trending, auth, access) } }
        val body = client.get("/trending?service=0") { authHeader() }.bodyAsText()
        assertTrue(body.contains("\"title\":\"Allowed\""))
        assertFalse(body.contains("\"title\":\"Blocked\""))
    }

    @Test
    fun `channel returns forbidden when channel is not allowed`() = testApplication {
        enableAllowList()
        coEvery { channel.getChannel(any(), any(), any()) } returns ExtractionResult.Success(channelResponse("Blocked"))
        application { install(ContentNegotiation) { json() }; routing { channelRoutes(channel, auth, access) } }
        val response = client.get("/channel?url=https://youtube.com/@blocked") { authHeader() }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `channel filters videos when channel is allowed`() = testApplication {
        enableAllowList()
        coEvery { channel.getChannel(any(), any(), any()) } returns ExtractionResult.Success(channelResponse("Allowed"))
        application { install(ContentNegotiation) { json() }; routing { channelRoutes(channel, auth, access) } }
        val body = client.get("/channel?url=https://youtube.com/@allowed") { authHeader() }.bodyAsText()
        assertTrue(body.contains("\"title\":\"Allowed\""))
        assertFalse(body.contains("\"title\":\"Blocked\""))
    }

    private suspend fun enableAllowList() {
        settings.upsert(TEST_USER_ID, SettingsItem(accessMode = "allow_list"))
        allowed.addChannel(TEST_USER_ID, "https://youtube.com/@allowed", "Allowed")
    }

    private fun video(name: String, url: String): VideoItem =
        testVideoItem().copy(title = name, uploaderName = name, uploaderUrl = url)

    private fun channelResponse(name: String) = ChannelResponse(
        name = name,
        description = "",
        avatarUrl = "",
        bannerUrl = "",
        subscriberCount = 0L,
        isVerified = false,
        videos = listOf(video("Allowed", "https://youtube.com/@allowed"), video("Blocked", "https://youtube.com/@blocked")),
        nextpage = null,
    )

    private fun io.ktor.client.request.HttpRequestBuilder.authHeader() {
        headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
    }
}
