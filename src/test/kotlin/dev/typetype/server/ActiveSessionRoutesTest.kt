package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.routes.adminSessionRoutes
import dev.typetype.server.routes.sessionActivityRoutes
import dev.typetype.server.services.ActiveSessionService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ActiveSessionRoutesTest {
    private val adminSettings = AdminSettingsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    private fun withApp(service: ActiveSessionService, block: suspend ApplicationTestBuilder.() -> Unit): Unit = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                sessionActivityRoutes(auth, service)
                adminSessionRoutes(auth, service)
            }
        }
        block()
    }

    @Test
    fun `disabled setting makes reporting a no-op`(): Unit = withApp(ActiveSessionService(adminSettings)) {
        insertActiveSessionUser()
        val report = client.post("/sessions/playback/start") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.UserAgent, "UA")
            header("X-Real-IP", "192.168.1.42")
            contentType(ContentType.Application.Json)
            setBody(startBody())
        }
        val list = client.get("/admin/sessions") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.NoContent, report.status)
        assertEquals("[]", list.bodyAsText())
    }

    @Test
    fun `enabled setting exposes active session without ip`(): Unit = withApp(ActiveSessionService(adminSettings)) {
        insertActiveSessionUser(publicUsername = "viewer")
        adminSettings.upsert(AdminSettingsItem(activeSessionsEnabled = true))
        client.post("/sessions/playback/start") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.UserAgent, "A".repeat(250))
            header("X-Real-IP", "192.168.1.42")
            contentType(ContentType.Application.Json)
            setBody(startBody())
        }
        val root = Json.parseToJsonElement(client.get("/admin/sessions") { header(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()).jsonArray
        val item = root.first().jsonObject
        assertEquals(1, root.size)
        assertEquals("viewer", item["username"]?.jsonPrimitive?.contentOrNull)
        assertEquals(200, item["userAgent"]?.jsonPrimitive?.contentOrNull?.length)
        assertEquals(null, item["remoteAddress"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Video", item["nowPlaying"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `stop clears now playing but keeps session active`(): Unit = withApp(ActiveSessionService(adminSettings)) {
        insertActiveSessionUser()
        adminSettings.upsert(AdminSettingsItem(activeSessionsEnabled = true))
        client.post("/sessions/playback/start") { header(HttpHeaders.Authorization, "Bearer test-jwt"); contentType(ContentType.Application.Json); setBody(startBody()) }
        client.post("/sessions/playback/stop") { header(HttpHeaders.Authorization, "Bearer test-jwt"); contentType(ContentType.Application.Json); setBody(deviceBody()) }
        val item = Json.parseToJsonElement(client.get("/admin/sessions") { header(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()).jsonArray.first().jsonObject
        assertEquals(null, item["nowPlaying"]?.jsonPrimitive?.contentOrNull)
    }

    private fun startBody(): String = """{"clientName":"web","deviceId":"device-1","videoUrl":"https://yt.test/watch?v=1","title":"Video","positionMs":1000,"paused":false}"""

    private fun deviceBody(): String = """{"clientName":"web","deviceId":"device-1"}"""
}
