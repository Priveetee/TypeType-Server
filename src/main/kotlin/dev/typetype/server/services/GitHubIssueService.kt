package dev.typetype.server.services

import dev.typetype.server.models.AdminBugReportDetailResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface GitHubIssueCreateResult {
    data class Success(val url: String) : GitHubIssueCreateResult
    data class Failure(val message: String) : GitHubIssueCreateResult
}

fun interface BugReportGitHubIssueService {
    suspend fun createIssue(report: AdminBugReportDetailResponse): GitHubIssueCreateResult
}

class GitHubIssueService(
    private val repo: String = System.getenv("GITHUB_REPO")?.takeIf { it.isNotBlank() } ?: DEFAULT_REPO,
    private val template: String? = System.getenv("GITHUB_ISSUE_TEMPLATE")?.takeIf { it.isNotBlank() },
) : BugReportGitHubIssueService {
    override suspend fun createIssue(report: AdminBugReportDetailResponse): GitHubIssueCreateResult {
        if (!repo.matches(REPO_PATTERN)) return GitHubIssueCreateResult.Failure("GitHub repository is invalid")
        val title = "[Bug][${report.category}] ${report.description.take(80)}"
        val body = buildIssueBody(report)
        val url = buildIssueUrl(title = title, body = body)
        return GitHubIssueCreateResult.Success(url)
    }

    private fun buildIssueBody(report: AdminBugReportDetailResponse): String = buildString {
        appendLine("## Bug report")
        appendLine(report.description)
        appendLine()
        appendLine("## Context")
        appendLine("- User: ${report.userEmail} (${report.userId})")
        appendLine("- Route: ${report.context.route}")
        appendLine("- Video: ${report.context.videoUrl ?: "n/a"}")
        appendLine("- Browser language: ${report.context.browserLanguage}")
        appendLine("- User agent: ${report.context.userAgent}")
        appendLine("- Timestamp: ${report.context.timestamp}")
        appendLine("- Crash logs: ${report.context.crashLogs.size}")
        appendLine()
        appendLine("## Player state")
        appendLine(report.context.playerState?.toString() ?: "{}")
    }

    private fun buildIssueUrl(title: String, body: String): String {
        val params = mutableListOf(
            "title=${encode(title)}",
            "body=${encode(body)}",
            "labels=${encode("bug")}",
        )
        if (template != null) params += "template=${encode(template)}"
        return "https://github.com/$repo/issues/new?${params.joinToString("&")}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val DEFAULT_REPO: String = "Priveetee/TypeType"
        val REPO_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    }
}
