package dev.typetype.server.services

internal object SabrPumpPolicy {
    const val IDLE_POLL_MS = 400L
    const val ERROR_RETRY_MS = 1_000L
    const val MAX_CONSECUTIVE_IO_ERRORS = 5
    const val READAHEAD_CUSHION_MS = 30_000L
    const val MAX_AHEAD_BYTES = 100L * 1024L * 1024L
    const val BACK_BUFFER_MS = 30_000L
    const val MIN_BACK_BUFFER_MS = 5_000L
    const val BACK_BUFFER_BYTES = 16L * 1024L * 1024L

    fun backBufferMs(holder: SabrSessionHolder): Long {
        val cachedBytes = runCatchingNonCancellation { holder.session.cachedBytes }.getOrDefault(0L)
        if (cachedBytes > MAX_AHEAD_BYTES) return MIN_BACK_BUFFER_MS
        val bitsPerSecond = holder.videoFormat.bitrate.toLong() + holder.audioFormat.bitrate.coerceAtLeast(0).toLong()
        if (bitsPerSecond <= 0L) return BACK_BUFFER_MS
        val bytesPerMs = (bitsPerSecond / 8L / 1_000L).coerceAtLeast(1L)
        return (BACK_BUFFER_BYTES / bytesPerMs).coerceIn(MIN_BACK_BUFFER_MS, BACK_BUFFER_MS)
    }
}
