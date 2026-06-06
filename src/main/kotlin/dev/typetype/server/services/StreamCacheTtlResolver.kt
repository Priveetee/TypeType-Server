package dev.typetype.server.services

import dev.typetype.server.models.StreamResponse

private const val DEFAULT_STREAM_TTL_SECONDS = 21_600L
private const val BILIBILI_SIGNED_STREAM_MAX_TTL_SECONDS = 3_600L
private const val SIGNED_STREAM_DEADLINE_SAFETY_SECONDS = 300L
private const val MIN_CACHEABLE_STREAM_TTL_SECONDS = 60L

internal fun StreamResponse.streamCacheTtlSeconds(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Long {
    val deadline = signedMediaUrls().mapNotNull { it.bilibiliDeadline() }.minOrNull()
        ?: return DEFAULT_STREAM_TTL_SECONDS
    val ttl = deadline - nowEpochSeconds - SIGNED_STREAM_DEADLINE_SAFETY_SECONDS
    if (ttl < MIN_CACHEABLE_STREAM_TTL_SECONDS) return 0L
    return minOf(ttl, BILIBILI_SIGNED_STREAM_MAX_TTL_SECONDS)
}

private fun StreamResponse.signedMediaUrls(): Sequence<String> = sequence {
    videoStreams.forEach { yield(it.url) }
    videoOnlyStreams.forEach { yield(it.url) }
    audioStreams.forEach { yield(it.url) }
}

private fun String.bilibiliDeadline(): Long? {
    if (!isBilibiliSignedMediaUrl()) return null
    return Regex("""[?&]deadline=(\d+)""").find(this)?.groupValues?.get(1)?.toLongOrNull()
        ?: Regex("""[?&]hdnts=exp=(\d+)""").find(this)?.groupValues?.get(1)?.toLongOrNull()
}

private fun String.isBilibiliSignedMediaUrl(): Boolean =
    contains("bilibili", ignoreCase = true) ||
        contains("bilivideo", ignoreCase = true) ||
        contains("hdslb.com", ignoreCase = true) ||
        contains("akamaized", ignoreCase = true)
