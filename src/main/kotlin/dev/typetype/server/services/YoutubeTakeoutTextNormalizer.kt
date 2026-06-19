package dev.typetype.server.services

import java.text.Normalizer

object YoutubeTakeoutTextNormalizer {
    fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}
