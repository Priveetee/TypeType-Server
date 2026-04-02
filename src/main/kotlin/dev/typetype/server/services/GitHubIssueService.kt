package dev.typetype.server.services

import dev.typetype.server.models.AdminBugReportDetailResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface GitHubIssueCreateResult {
    data class Success(val url: String) : GitHubIssueCreateResult
    data class NotConfigured(val message: String) : GitHubIssueCreateResult
    data class Failure(val message: String) : GitHubIssueCreateResult
}

fun interface BugReportGitHubIssueService {
    suspend fun createIssue(report: AdminBugReportDetailResponse): GitHubIssueCreateResult
}

class GitHubIssueService(
    private val client: OkHttpClient = OkHttpClient(),
    private val token: String? = System.getenv("GITHUB_TOKEN"),
    private val repo: String? = System.getenv("GITHUB_REPO"),
) : BugReportGitHubIssueService {
    override suspend fun createIssue(report: AdminBugReportDetailResponse): GitHubIssueCreateResult = withContext(Dispatchers.IO) {
        val ghToken = token?.takeIf { it.isNotBlank() }
        val ghRepo = repo?.takeIf { it.isNotBlank() }
        if (ghToken == null || ghRepo == null) return@withContext GitHubIssueCreateResult.NotConfigured("GitHub integration is not configured")
        val title = "[Bug][${report.category}] ${report.description.take(80)}"
        val body = buildIssueBody(report)
        val payload = buildJsonObject {
            put("title", title)
            put("body", body)
        }.toString()
        val request = Request.Builder()
            .url("https://api.github.com/repos/$ghRepo/issues")
            .addHeader("Authorization", "Bearer $ghToken")
            .addHeader("Accept", "application/vnd.github+json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { client.newCall(request).execute() }.getOrElse {
            return@withContext GitHubIssueCreateResult.Failure(it.message ?: "GitHub issue creation failed")
        }.use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 201) {
                val url = runCatching { Json.parseToJsonElement(text).jsonObject["html_url"]?.toString()?.trim('"') }.getOrNull()
                return@withContext if (url.isNullOrBlank()) GitHubIssueCreateResult.Failure("GitHub issue URL missing in response") else GitHubIssueCreateResult.Success(url)
            }
            GitHubIssueCreateResult.Failure(text.take(500))
        }
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
}
