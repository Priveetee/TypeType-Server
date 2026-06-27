package dev.typetype.server.services

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

object YoutubeTakeoutDateParser {
    fun parseEpochMillis(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        parseOffset(trimmed)?.let { return it }
        parseInstant(trimmed)?.let { return it }
        return parseDate(trimmed)
    }

    private fun parseOffset(value: String): Long? = runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrNull()

    private fun parseInstant(value: String): Long? = runCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrNull()

    private fun parseDate(value: String): Long? = runCatching {
        LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()
}
