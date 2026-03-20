package dev.typetype.server

import dev.typetype.server.routes.restoreRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PipePipeBackupImporterService
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RestoreRoutesSecurityTest {

    private val auth = AuthService.fixed(TEST_USER_ID)
    private val importer = PipePipeBackupImporterService()

    @Test
    fun `POST restore rejects non zip file type`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { restoreRoutes(importer, auth) }
        }
        val response = client.post("/restore/pipepipe") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            setBody(MultiPartFormDataContent(formData {
                append("file", "not-zip".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                    append(HttpHeaders.ContentDisposition, "filename=bad.txt")
                })
            }))
        }
        assertTrue(response.bodyAsText().contains("Invalid backup file type"))
        assertTrue(response.status == HttpStatusCode.BadRequest)
    }

    @Test
    fun `POST restore rejects multiple file parts`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { restoreRoutes(importer, auth) }
        }
        val response = client.post("/restore/pipepipe") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            setBody(MultiPartFormDataContent(formData {
                append("file", byteArrayOf(1), Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    append(HttpHeaders.ContentDisposition, "filename=a.zip")
                })
                append("file", byteArrayOf(2), Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    append(HttpHeaders.ContentDisposition, "filename=b.zip")
                })
            }))
        }
        assertTrue(response.bodyAsText().contains("Only one backup file is allowed"))
        assertTrue(response.status == HttpStatusCode.BadRequest)
    }

    @Test
    fun `POST restore hides internal import errors`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { restoreRoutes(importer, auth) }
        }
        val response = client.post("/restore/pipepipe") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            setBody(MultiPartFormDataContent(formData {
                append("file", "invalid-zip-content".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    append(HttpHeaders.ContentDisposition, "filename=broken.zip")
                })
            }))
        }
        val body = response.bodyAsText()
        assertTrue(response.status == HttpStatusCode.BadRequest)
        assertTrue(body.contains("Invalid backup archive"))
        assertFalse(body.contains("SQLITE"))
    }

    @Test
    fun `POST restore rejects invalid time mode`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { restoreRoutes(importer, auth) }
        }
        val response = client.post("/restore/pipepipe?timeMode=oops") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            setBody(MultiPartFormDataContent(formData {
                append("file", byteArrayOf(1), Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    append(HttpHeaders.ContentDisposition, "filename=a.zip")
                })
            }))
        }
        assertTrue(response.status == HttpStatusCode.BadRequest)
        assertTrue(response.bodyAsText().contains("Invalid timeMode"))
    }
}
