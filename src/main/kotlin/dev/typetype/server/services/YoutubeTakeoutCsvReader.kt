package dev.typetype.server.services

import java.io.BufferedReader

object YoutubeTakeoutCsvReader {
    fun parse(reader: BufferedReader): Pair<List<String>, List<List<String>>> {
        val lines = reader.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList<String>() to emptyList()
        val header = splitLine(lines.first())
        val rows = lines.drop(1).map { line -> splitLine(line) }
        return header to rows
    }

    private fun splitLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        line.forEach { c ->
            when {
                c == '"' -> quoted = quoted.not()
                c == ',' && !quoted -> {
                    values += current.toString().trim().trim('"')
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        values += current.toString().trim().trim('"')
        return values
    }
}
