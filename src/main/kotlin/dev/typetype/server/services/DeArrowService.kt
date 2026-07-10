package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.DeArrowItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

class DeArrowService(
    private val cache: CacheService,
    private val client: DeArrowRemote = DeArrowClient(),
) {
    suspend fun get(videoId: String): DeArrowItem? {
        if (!isValidVideoId(videoId)) return null
        cache.get("dearrow:branding:$videoId")?.let {
            return runCatching { CacheJson.decodeFromString(DeArrowItem.serializer(), it) }.getOrNull()
        }
        val item = client.branding(videoId)?.let { parse(videoId, it) } ?: DeArrowItem(videoId)
        cache.set(
            "dearrow:branding:$videoId",
            CacheJson.encodeToString(DeArrowItem.serializer(), item),
            BRANDING_TTL_SECONDS,
        )
        return item
    }

    suspend fun thumbnail(videoId: String, timestamp: Double): ByteArray? {
        if (!isValidVideoId(videoId) || !timestamp.isFinite() || timestamp < 0.0) return null
        val normalizedTime = "%.3f".format(java.util.Locale.ROOT, timestamp)
        val key = "dearrow:thumbnail:$videoId:$normalizedTime"
        cache.get(key)?.let { return runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        val bytes = client.thumbnail(videoId, timestamp)?.takeIf { it.size <= MAX_THUMBNAIL_BYTES } ?: return null
        cache.set(key, Base64.getEncoder().encodeToString(bytes), THUMBNAIL_TTL_SECONDS)
        return bytes
    }

    private fun parse(videoId: String, raw: String): DeArrowItem {
        val root = runCatching { CacheJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return DeArrowItem(videoId)
        val title = bestEntry(root["titles"] as? JsonArray)?.get("title")?.jsonPrimitive?.content
        val thumbnail = bestEntry(root["thumbnails"] as? JsonArray)
        val timestamp = thumbnail?.get("timestamp")?.jsonPrimitive?.doubleOrNull
            ?: randomTimestamp(root)
        val thumbnailUrl = timestamp?.takeIf { it > 0.0 }?.let { "/dearrow/thumbnail?videoId=$videoId&time=$it" }
        val normalizedTitle = title?.replace(TITLE_MARKER_REGEX, "")?.takeIf { it.isNotBlank() }
        return DeArrowItem(videoId = videoId, title = normalizedTitle, thumbnailUrl = thumbnailUrl)
    }

    private fun bestEntry(entries: JsonArray?): JsonObject? = entries
        ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        ?.firstOrNull { entry ->
            val original = entry["original"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val locked = entry["locked"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val votes = entry["votes"]?.jsonPrimitive?.intOrNull ?: -1
            !original && (locked || votes >= 0)
        }

    private fun randomTimestamp(root: JsonObject): Double? {
        val duration = root["videoDuration"]?.jsonPrimitive?.doubleOrNull ?: return null
        val randomTime = root["randomTime"]?.jsonPrimitive?.doubleOrNull ?: return null
        return duration * randomTime
    }

    fun isValidVideoId(videoId: String): Boolean = VIDEO_ID_REGEX.matches(videoId)

    companion object {
        private const val BRANDING_TTL_SECONDS = 86_400L
        private const val THUMBNAIL_TTL_SECONDS = 604_800L
        private const val MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024
        private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
        private val TITLE_MARKER_REGEX = Regex(">(?=\\S)")
    }
}
