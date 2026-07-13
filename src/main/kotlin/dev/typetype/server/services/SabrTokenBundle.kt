package dev.typetype.server.services

import org.json.JSONObject
import java.util.Base64

internal class SabrTokenBundle(
    val videoId: String,
    val visitorBoundPoToken: String,
    val visitorBoundPoTokenBytes: ByteArray,
    val visitorData: String,
    val videoBoundPoToken: String,
    val videoBoundPoTokenBytes: ByteArray,
) {
    val visitorPoToken: String = visitorBoundPoToken
    val visitorPoTokenBytes: ByteArray = visitorBoundPoTokenBytes
    val streamingPoToken: String = videoBoundPoToken
    val streamingPoTokenBytes: ByteArray = videoBoundPoTokenBytes

    companion object {
        fun fromResponse(videoId: String, json: JSONObject): SabrTokenBundle? = runCatching {
            val visitorBoundPoToken = json.optString("visitorBoundPoToken").ifBlank { json.optString("poToken") }
            val visitorData = json.optString("visitorData")
            val videoBoundPoToken = json.optString("videoBoundPoToken").ifBlank { json.optString("streamingPot") }
            if (visitorBoundPoToken.isBlank() || visitorData.isBlank() || videoBoundPoToken.isBlank()) return null
            SabrTokenBundle(
                videoId = videoId,
                visitorBoundPoToken = visitorBoundPoToken,
                visitorBoundPoTokenBytes = decodeBase64Url(visitorBoundPoToken),
                visitorData = visitorData,
                videoBoundPoToken = videoBoundPoToken,
                videoBoundPoTokenBytes = decodeBase64Url(videoBoundPoToken),
            )
        }.getOrNull()

        private fun decodeBase64Url(value: String): ByteArray {
            val padded = value + "=".repeat((4 - value.length % 4) % 4)
            return Base64.getUrlDecoder().decode(padded)
        }
    }
}
