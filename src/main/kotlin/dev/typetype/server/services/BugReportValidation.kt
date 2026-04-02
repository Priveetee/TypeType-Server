package dev.typetype.server.services

import dev.typetype.server.models.BugReportContextItem

internal object BugReportValidation {
    private val categories = setOf("player", "audio_language", "subtitles", "ui", "functionality")
    private val statuses = setOf("new", "triaged", "in_progress", "fixed", "closed")

    fun validateCategory(value: String): String? =
        if (value in categories) null else "Invalid category"

    fun validateStatus(value: String): String? =
        if (value in statuses) null else "Invalid status"

    fun validateDescription(value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return "Description is required"
        if (text.length > 10_000) return "Description is too long"
        return null
    }

    fun validateContext(context: BugReportContextItem): String? {
        if (context.route.isBlank()) return "Context route is required"
        if (context.userAgent.isBlank()) return "Context userAgent is required"
        if (context.browserLanguage.isBlank()) return "Context browserLanguage is required"
        if (context.timestamp <= 0) return "Context timestamp is invalid"
        if (context.crashLogs.size > 200) return "Too many crash logs"
        return null
    }
}
