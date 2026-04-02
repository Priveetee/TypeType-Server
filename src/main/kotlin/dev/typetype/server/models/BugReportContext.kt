package dev.typetype.server.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BugCrashLogItem(
    val message: String,
    val stack: String? = null,
    val timestamp: Long,
)

@Serializable
data class BugReportContextItem(
    val videoUrl: String? = null,
    val route: String,
    val timestamp: Long,
    val userAgent: String,
    val browserLanguage: String,
    val playerState: JsonElement? = null,
    val crashLogs: List<BugCrashLogItem> = emptyList(),
)
