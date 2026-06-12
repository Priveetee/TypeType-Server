package dev.typetype.server

import dev.typetype.server.routes.settingsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SettingsService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsSponsorBlockAdvancedRoutesTest {
    private val service = SettingsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() {
            TestDatabase.setup()
        }
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing { settingsRoutes(service, auth) }
        }
        block()
    }

    @Test
    fun `GET settings returns advanced SponsorBlock defaults`() = withApp {
        val body = authorizedGetSettings()

        assertContainsAll(
            body,
            listOf(
                "\"sponsorBlockMinimumDuration\":0",
                "\"sponsorBlockShowCurrentSegment\":true",
                "\"sponsorBlockShowChapters\":false",
                "\"sponsorBlockShowFullVideoLabels\":true",
                "\"sponsorBlockManualSkipOnFullVideo\":true",
                "\"sponsorBlockSkipNonMusicOnlyOnMusicVideos\":false",
                "\"sponsorBlockMuteInsteadOfSkip\":false",
                "\"sponsor\":\"auto_skip\"",
                "\"exclusive_access\":\"mark_only\"",
                "\"music_offtopic\":\"auto_skip\"",
            ),
        )
    }

    @Test
    fun `PUT settings persists advanced SponsorBlock controls`() = withApp {
        val response = client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(advancedBody())
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = authorizedGetSettings()
        assertContainsAll(
            body,
            listOf(
                "\"sponsor\":\"disabled\"",
                "\"selfpromo\":\"auto_skip\"",
                "\"sponsorBlockMinimumDuration\":3",
                "\"sponsorBlockShowCurrentSegment\":false",
                "\"sponsorBlockShowChapters\":true",
                "\"sponsorBlockShowFullVideoLabels\":false",
                "\"sponsorBlockManualSkipOnFullVideo\":false",
                "\"sponsorBlockSkipNonMusicOnlyOnMusicVideos\":true",
                "\"sponsorBlockMuteInsteadOfSkip\":true",
            ),
        )
    }

    @Test
    fun `PUT settings rejects invalid SponsorBlock category action`() = withApp {
        val response = client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"sponsorBlockCategoryActions":{"sponsor":"bad_action"}}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private suspend fun ApplicationTestBuilder.authorizedGetSettings(): String =
        client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()

    private fun assertContainsAll(body: String, values: List<String>) =
        values.forEach { assertTrue(body.contains(it)) }

    private fun advancedBody(): String = """
        {"sponsorBlockCategoryActions":{"sponsor":"disabled"},"sponsorBlockMinimumDuration":3,"sponsorBlockShowCurrentSegment":false,"sponsorBlockShowChapters":true,"sponsorBlockShowFullVideoLabels":false,"sponsorBlockManualSkipOnFullVideo":false,"sponsorBlockSkipNonMusicOnlyOnMusicVideos":true,"sponsorBlockMuteInsteadOfSkip":true}
    """.trimIndent()
}
