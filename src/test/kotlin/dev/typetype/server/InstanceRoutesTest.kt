package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.routes.authRoutes
import dev.typetype.server.routes.publicMetadataRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.InstanceService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import io.ktor.client.request.get
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InstanceRoutesTest {
    private val adminSettings = AdminSettingsService()
    private val passwordReset = PasswordResetService()
    private val profile = ProfileService()

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
    fun `instance returns defaults and cache header`() = testApplication {
        val auth = AuthService.fixed(TEST_USER_ID, hasUsers = false)
        val instanceService = InstanceService(auth, adminSettings)
        application {
            install(ContentNegotiation) { json() }
            routing { publicMetadataRoutes(instanceService::getInstance) }
        }
        val response = client.get("/instance")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("public, max-age=300", response.headers[HttpHeaders.CacheControl])
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("TypeType", root["name"]?.jsonPrimitive?.contentOrNull)
        assertEquals(BuildInfo.VERSION, root["version"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1, root["apiVersion"]?.jsonPrimitive?.int)
        assertEquals(true, root["registrationAllowed"]?.jsonPrimitive?.boolean)
        assertEquals(true, root["guestAllowed"]?.jsonPrimitive?.boolean)
        assertEquals(listOf(0, 3, 4, 5, 6), root["supportedServices"]?.jsonArray?.map { it.jsonPrimitive.int })
        assertEquals(null, root["logoUrl"]?.jsonPrimitive?.contentOrNull)
        assertEquals(null, root["bannerUrl"]?.jsonPrimitive?.contentOrNull)
        assertEquals(null, root["minClientVersion"]?.jsonObject?.get("android")?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `instance reflects custom settings and blocks registration when users exist`() = testApplication {
        adminSettings.upsert(
            AdminSettingsItem(
                name = "Custom Instance",
                tagline = "Privacy-respecting video platform",
                logoUrl = "https://cdn.example.com/typetype/logo.png",
                bannerUrl = "https://cdn.example.com/typetype/banner.jpg",
                minAndroidClientVersion = "0.1.0",
                allowRegistration = false,
                allowGuest = false,
            )
        )
        val auth = AuthService.fixed(TEST_USER_ID, hasUsers = true)
        val instanceService = InstanceService(auth, adminSettings)
        application {
            install(ContentNegotiation) { json() }
            routing {
                publicMetadataRoutes(instanceService::getInstance)
                authRoutes(auth, passwordReset, profile, adminSettings)
            }
        }
        val response = client.get("/instance")
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Custom Instance", root["name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Privacy-respecting video platform", root["tagline"]?.jsonPrimitive?.contentOrNull)
        assertEquals("https://cdn.example.com/typetype/logo.png", root["logoUrl"]?.jsonPrimitive?.contentOrNull)
        assertEquals("https://cdn.example.com/typetype/banner.jpg", root["bannerUrl"]?.jsonPrimitive?.contentOrNull)
        assertEquals("0.1.0", root["minClientVersion"]?.jsonObject?.get("android")?.jsonPrimitive?.contentOrNull)
        assertEquals(false, root["registrationAllowed"]?.jsonPrimitive?.boolean)
        assertEquals(false, root["guestAllowed"]?.jsonPrimitive?.boolean)
        val register = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"new@test.local","password":"secret","name":"New"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, register.status)
    }
}
