package dev.typetype.server.services

import java.io.BufferedReader

object YoutubeTakeoutCsvReader {
    fun parse(reader: BufferedReader): Pair<List<String>, List<List<String>>> {
        val records = parseRecords(reader.readText()).filter { row -> row.any { it.isNotBlank() } }
        if (records.isEmpty()) return emptyList<String>() to emptyList()
        val header = records.first().mapIndexed { index, value ->
            if (index == 0) value.removePrefix("\uFEFF") else value
        }
        return header to records.drop(1)
    }

    private fun parseRecords(content: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < content.length) {
            val c = content[index]
            when {
                c == '"' && quoted && content.getOrNull(index + 1) == '"' -> {
                    current.append(c)
                    index += 1
                }
                c == '"' -> quoted = quoted.not()
                c == ',' && !quoted -> {
                    row += current.toString().trim()
                    current.clear()
                }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && content.getOrNull(index + 1) == '\n') index += 1
                    row += current.toString().trim()
                    records += row.toList()
                    row.clear()
                    current.clear()
                }
                else -> current.append(c)
            }
            index += 1
        }
        row += current.toString().trim()
        records += row.toList()
        return records
    }
}
