package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal sealed interface SabrWebSocketItagValidation {
    data object Valid : SabrWebSocketItagValidation

    data class Invalid(
        val code: String,
        val message: String,
    ) : SabrWebSocketItagValidation
}

internal fun SabrSessionHolder.applySabrWebSocketState(message: JsonObject): Unit {
    val audioActive = message.sabrBoolean("audioActive") ?: isAudioActive()
    val videoActive = message.sabrBoolean("videoActive") ?: isVideoActive()
    setActiveTracks(videoActive = videoActive, audioActive = audioActive)
    message.sabrLong("playerTimeMs")?.let {
        setPlayerTimeMs(it)
        session.streamState.setPlayerTimeMs(it)
    }
    message.sabrFloat("playbackRate")?.let { session.streamState.setPlaybackRate(it) }
}

internal fun SabrSessionHolder.sabrTargetRequest(message: JsonObject, init: Boolean): SabrSegmentRequest? {
    val format = message.sabrInt("itag")?.let { sabrFormatForItag(it) } ?: return null
    if (init) return SabrSegmentRequest.initialization(format)
    val sequence = message.sabrInt("sequence")?.takeIf { it > 0 } ?: return null
    return SabrSegmentRequest.media(format, sequence)
}

internal fun SabrSessionHolder.validateSabrWebSocketItags(message: JsonObject): SabrWebSocketItagValidation {
    val audioItag = message.sabrInt("audioItag")
    if (audioItag != null && audioItag != audioFormat.itag) {
        return SabrWebSocketItagValidation.Invalid("itag_mismatch", "audioItag does not match SABR session")
    }
    val videoItag = message.sabrInt("videoItag")
    if (videoItag != null && videoItag != videoFormat.itag) {
        return SabrWebSocketItagValidation.Invalid("itag_mismatch", "videoItag does not match SABR session")
    }
    return SabrWebSocketItagValidation.Valid
}

internal fun JsonObject.hasSabrTargetFields(): Boolean = containsKey("itag") || containsKey("sequence")

internal fun JsonObject.hasSabrActiveTrack(holder: SabrSessionHolder): Boolean {
    val videoActive = sabrBoolean("videoActive") ?: holder.isVideoActive()
    val audioActive = sabrBoolean("audioActive") ?: holder.isAudioActive()
    return videoActive || audioActive
}

internal fun JsonObject.sabrString(name: String): String? = this[name]?.jsonPrimitive?.content

internal fun JsonObject.sabrLong(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

private fun JsonObject.sabrBoolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull

private fun JsonObject.sabrInt(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

private fun JsonObject.sabrFloat(name: String): Float? = this[name]?.jsonPrimitive?.doubleOrNull?.toFloat()

private fun SabrSessionHolder.sabrFormatForItag(itag: Int): YoutubeSabrFormat? = when (itag) {
    audioFormat.itag -> audioFormat
    videoFormat.itag -> videoFormat
    else -> null
}
