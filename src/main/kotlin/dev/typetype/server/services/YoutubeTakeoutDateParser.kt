package dev.typetype.server.services

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object YoutubeTakeoutDateParser {
    private val activityFormatters = listOf(
        Locale.ENGLISH,
        Locale.FRENCH,
    ).flatMap { locale ->
        listOf("d MMM yyyy, HH:mm:ss z", "d MMMM yyyy, HH:mm:ss z").map { pattern ->
            DateTimeFormatter.ofPattern(pattern, locale)
        }
    }

    fun parseEpochMillis(value: String): Long? {
        val trimmed = value.replace("\u00a0", " ").trim()
        if (trimmed.isBlank()) return null
        parseOffset(trimmed)?.let { return it }
        parseInstant(trimmed)?.let { return it }
        parseDate(trimmed)?.let { return it }
        return parseActivityDate(trimmed)
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

    private fun parseActivityDate(value: String): Long? {
        activityFormatters.forEach { formatter ->
            runCatching { ZonedDateTime.parse(value, formatter).toInstant().toEpochMilli() }
                .getOrNull()
                ?.let { return it }
        }
        return null
    }
}
