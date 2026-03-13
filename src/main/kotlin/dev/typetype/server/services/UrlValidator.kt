package dev.typetype.server.services

import java.net.InetAddress
import java.net.URI

private val BLOCKED_HOST_SUFFIXES = listOf(".local", ".internal", ".localhost")
private val PRIVATE_RANGES = listOf(
    intArrayOf(10, 0, 0, 0) to 8,
    intArrayOf(172, 16, 0, 0) to 12,
    intArrayOf(192, 168, 0, 0) to 16,
    intArrayOf(127, 0, 0, 0) to 8,
    intArrayOf(169, 254, 0, 0) to 16,
    intArrayOf(0, 0, 0, 0) to 8,
)

internal fun validateProxyUrl(raw: String): String? {
    val uri = runCatching { URI(raw) }.getOrElse { return "Malformed URL" }
    val scheme = uri.scheme?.lowercase() ?: return "Missing URL scheme"
    if (scheme != "http" && scheme != "https") return "Unsupported URL scheme: $scheme"
    val host = uri.host?.lowercase() ?: return "Missing URL host"
    if (host == "localhost") return "Blocked host"
    if (BLOCKED_HOST_SUFFIXES.any { host.endsWith(it) }) return "Blocked host"
    val addr = runCatching { InetAddress.getByName(host) }.getOrElse { return null }
    val bytes = addr.address
    if (bytes.size != 4) return null
    val octets = bytes.map { it.toInt() and 0xFF }
    for ((prefix, bits) in PRIVATE_RANGES) {
        if (isInRange(octets, prefix, bits)) return "Blocked private address"
    }
    return null
}

private fun isInRange(octets: List<Int>, prefix: IntArray, bits: Int): Boolean {
    var remaining = bits
    for (i in prefix.indices) {
        val maskBits = remaining.coerceIn(0, 8)
        val mask = if (maskBits == 0) 0 else (0xFF shl (8 - maskBits)) and 0xFF
        if ((octets[i] and mask) != (prefix[i] and mask)) return false
        remaining -= maskBits
        if (remaining <= 0) break
    }
    return true
}
