package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.routes.adminBugReportRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BugReportGitHubIssueService
import dev.typetype.server.services.BugReportService
import dev.typetype.server.services.GitHubIssueCreateResult
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminBugReportDetailRoutesTest {
    private val service = BugReportService()
    private val moderatorAuth = AuthService.fixed("mod-id")

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        transaction {
            UsersTable.insert {
                it[id] = "mod-id"
                it[email] = "mod@test.local"
                it[passwordHash] = "hash"
                it[name] = "Mod"
                it[role] = "moderator"
                it[createdAt] = 1L
                it[updatedAt] = 1L
            }
            UsersTable.insert {
                it[id] = TEST_USER_ID
                it[email] = "user@test.local"
                it[passwordHash] = "hash"
                it[name] = "User"
                it[role] = "user"
                it[createdAt] = 1L
                it[updatedAt] = 1L
            }
        }
    }

    @Test
    fun `GET admin bug report detail returns context`() = testApplication {
        val created = service.create(TEST_USER_ID, request())
        application {
            install(ContentNegotiation) { json() }
            routing { adminBugReportRoutes(moderatorAuth, service, noConfigIssueService()) }
        }
        val response = client.get("/admin/bug-reports/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"context\""))
    }

    private fun request() = dev.typetype.server.models.CreateBugReportRequest(
        category = "player",
        description = "Video freezes",
        context = dev.typetype.server.models.BugReportContextItem(
            route = "/watch",
            timestamp = 1774200000000,
            userAgent = "Mozilla",
            browserLanguage = "fr-FR",
        ),
    )

    private fun noConfigIssueService(): BugReportGitHubIssueService =
        BugReportGitHubIssueService { GitHubIssueCreateResult.NotConfigured("GitHub integration is not configured") }
}
