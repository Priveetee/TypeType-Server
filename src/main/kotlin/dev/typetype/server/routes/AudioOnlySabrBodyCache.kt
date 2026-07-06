package dev.typetype.server.routes

internal object AudioOnlySabrBodyCache {
    private val bodies = LinkedHashMap<String, Entry>(8, 0.75f, true)
    private var totalBytes = 0L

    @Synchronized
    fun get(videoId: String, itag: Int, audioTrackId: String?): ByteArray? {
        val entry = bodies[key(videoId, itag, audioTrackId)] ?: return null
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            remove(key(videoId, itag, audioTrackId))
            return null
        }
        return entry.body
    }

    @Synchronized
    fun put(videoId: String, itag: Int, audioTrackId: String?, body: ByteArray): Unit {
        remove(key(videoId, itag, audioTrackId))
        bodies[key(videoId, itag, audioTrackId)] = Entry(
            body = body,
            expiresAtMs = System.currentTimeMillis() + TTL_MS,
        )
        totalBytes += body.size
        trim()
    }

    @Synchronized
    private fun remove(key: String): Unit {
        bodies.remove(key)?.let { totalBytes -= it.body.size }
    }

    @Synchronized
    private fun trim(): Unit {
        val iterator = bodies.iterator()
        val now = System.currentTimeMillis()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (entry.expiresAtMs <= now || totalBytes > MAX_BYTES) {
                totalBytes -= entry.body.size
                iterator.remove()
            }
        }
    }

    private fun key(videoId: String, itag: Int, audioTrackId: String?): String =
        listOf(videoId, itag.toString(), audioTrackId.orEmpty()).joinToString(":")

    private data class Entry(val body: ByteArray, val expiresAtMs: Long)

    private const val TTL_MS = 5 * 60 * 1_000L
    private const val MAX_BYTES = 64L * 1024L * 1024L
}
