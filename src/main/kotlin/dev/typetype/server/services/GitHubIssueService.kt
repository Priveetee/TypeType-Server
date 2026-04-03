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
        appendLine("- API errors: ${report.context.apiErrors.size}")
        val recentApiError = report.context.apiErrors.firstOrNull()
        if (recentApiError != null) {
            appendLine("- Latest API endpoint: ${recentApiError.endpoint}")
            appendLine("- Latest API status: ${recentApiError.status}")
            appendLine("- Latest API requestId: ${recentApiError.requestId ?: "n/a"}")
            appendLine("- Latest API code: ${recentApiError.code ?: "n/a"}")
        }
        appendLine()
        appendLine("## Player state")
        appendLine(report.context.playerState?.toString() ?: "{}")
        if (report.context.apiErrors.isNotEmpty()) {
            appendLine()
            appendLine("## API errors")
            report.context.apiErrors.take(10).forEach { error ->
                appendLine("- endpoint=${error.endpoint} status=${error.status} requestId=${error.requestId ?: "n/a"} code=${error.code ?: "n/a"} timestamp=${error.timestamp}")
                if (!error.message.isNullOrBlank()) appendLine("  message=${error.message}")
            }
        }
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
        const val DEFAULT_REPO: String = "Priveetee/TypeType-Server"
        val REPO_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    }
}
