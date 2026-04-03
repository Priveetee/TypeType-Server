package dev.typetype.server

import dev.typetype.server.models.AdminBugReportDetailResponse
import dev.typetype.server.models.BugApiErrorItem
import dev.typetype.server.models.BugReportContextItem
import dev.typetype.server.services.GitHubIssueCreateResult
import dev.typetype.server.services.GitHubIssueService
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitHubIssueServiceTest {
    @Test
    fun `default repo targets backend repository`() = runBlocking {
        val service = GitHubIssueService(repo = "Priveetee/TypeType-Server")
        val result = service.createIssue(sampleReport())
        assertTrue(result is GitHubIssueCreateResult.Success)
        val url = (result as GitHubIssueCreateResult.Success).url
        assertTrue(url.startsWith("https://github.com/Priveetee/TypeType-Server/issues/new?"))
    }

    @Test
    fun `issue body includes api error diagnostics`() = runBlocking {
        val service = GitHubIssueService(repo = "Priveetee/TypeType-Server")
        val result = service.createIssue(sampleReport())
        val url = (result as GitHubIssueCreateResult.Success).url
        assertTrue(url.contains("API+errors"))
        assertTrue(url.contains("requestId%3Dreq-123"))
        assertTrue(url.contains("code%3DBAD_REQUEST"))
    }

    @Test
    fun `issue body redacts domains from urls and identifiers`() = runBlocking {
        val service = GitHubIssueService(repo = "Priveetee/TypeType-Server")
        val result = service.createIssue(sampleReportWithDomain())
        val url = URLDecoder.decode((result as GitHubIssueCreateResult.Success).url, StandardCharsets.UTF_8)
        assertTrue(url.contains("/watch?url=abc"), url)
        assertTrue(url.contains("/api/streams?url=1"), url)
        assertTrue(!url.contains("internal.local"), url)
        assertTrue(!url.contains("private.example"), url)
    }

    @Test
    fun `issue body redacts hostile requestId and userAgent host tokens`() = runBlocking {
        val service = GitHubIssueService(repo = "Priveetee/TypeType-Server")
        val result = service.createIssue(sampleReportWithHostTokens())
        val url = URLDecoder.decode((result as GitHubIssueCreateResult.Success).url, StandardCharsets.UTF_8)
        assertTrue(!url.contains("private.host.internal"), url)
        assertTrue(!url.contains("req.private.local"), url)
        assertTrue(url.contains("requestId=redacted-host"), url)
        assertTrue(url.contains("User agent: Mozilla (redacted-host/client)"), url)
    }

    private fun sampleReport(): AdminBugReportDetailResponse = AdminBugReportDetailResponse(
        id = "report-id",
        category = "player",
        description = "Video freezes after 10s",
        status = "new",
        userId = "user-id",
        userEmail = "user@test.local",
        context = BugReportContextItem(
            route = "/watch",
            timestamp = 1775200000000,
            userAgent = "Mozilla/5.0",
            browserLanguage = "fr-FR",
            apiErrors = listOf(
                BugApiErrorItem(
                    requestId = "req-123",
                    endpoint = "/streams",
                    status = 400,
                    code = "BAD_REQUEST",
                    message = "Invalid url",
                    timestamp = 1775200000001,
                ),
            ),
        ),
        githubIssueUrl = null,
        createdAt = 1775200000000,
        updatedAt = 1775200000000,
    )

    private fun sampleReportWithDomain(): AdminBugReportDetailResponse = AdminBugReportDetailResponse(
        id = "report-id-2",
        category = "ui",
        description = "Fails on https://internal.local/watch?url=abc",
        status = "new",
        userId = "user-id-2",
        userEmail = "user@private.example",
        context = BugReportContextItem(
            route = "https://private.example/watch?url=abc",
            timestamp = 1775200001000,
            userAgent = "Mozilla/5.0 (see https://ua.example/meta)",
            browserLanguage = "fr-FR",
            videoUrl = "https://private.example/video?id=1",
            apiErrors = listOf(
                BugApiErrorItem(
                    requestId = "req-redact",
                    endpoint = "https://internal.local/api/streams?url=1",
                    status = 500,
                    code = "INTERNAL_ERROR",
                    message = "Upstream from https://internal.local failed",
                    timestamp = 1775200001001,
                ),
            ),
        ),
        githubIssueUrl = null,
        createdAt = 1775200001000,
        updatedAt = 1775200001000,
    )

    private fun sampleReportWithHostTokens(): AdminBugReportDetailResponse = AdminBugReportDetailResponse(
        id = "report-id-3",
        category = "functionality",
        description = "Host token redaction case",
        status = "new",
        userId = "user-id-3",
        userEmail = "user3@test.local",
        context = BugReportContextItem(
            route = "/shorts",
            timestamp = 1775200002000,
            userAgent = "Mozilla (private.host.internal/client)",
            browserLanguage = "fr-FR",
            apiErrors = listOf(
                BugApiErrorItem(
                    requestId = "req.private.local",
                    endpoint = "/admin/bug-reports",
                    status = 409,
                    code = "CONFLICT",
                    message = "already exists",
                    timestamp = 1775200002001,
                ),
            ),
        ),
        githubIssueUrl = null,
        createdAt = 1775200002000,
        updatedAt = 1775200002000,
    )
}
