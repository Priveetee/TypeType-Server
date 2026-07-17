package dev.typetype.server.services

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrBufferedRange
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import java.security.MessageDigest

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrLiveProtocolProbeTest {
    @Test
    fun `retrieves every requested live sabr codec`(): Unit = runBlocking {
        val videoId = sabrProbeVideoId()
        val tokenServiceUrl = sabrProbeTokenServiceUrl()
        NewPipeInitializer.init(tokenServiceUrl)
        val store = SabrSessionStore(tokenServiceUrl = tokenServiceUrl)
        try {
            val prepared = TypetypeTokenYoutubeSessionClient(tokenServiceUrl)
                .fetchPlaybackSession(videoId) ?: error("Missing Token SABR session")
            println(
                "profile=${prepared.info.profile} clientVersion=${prepared.info.clientVersion} " +
                    "visitorMatches=${prepared.info.visitorData == prepared.token?.visitorData}"
            )
            val audio = prepared.info.formats.first { it.itag == sabrProbeAudioItag() && it.isAudio }
            val requestedVideoItags = sabrProbeVideoItags()
            val videos = requestedVideoItags.map { itag ->
                prepared.info.formats.first { it.itag == itag && it.isVideo }
            }
            assertEquals(requestedVideoItags, videos.map { it.itag })
            videos.forEach { video ->
                probe(store, videoId, prepared, audio, video)
            }
        } finally {
            store.release()
        }
    }

    private fun probe(
        store: SabrSessionStore,
        videoId: String,
        prepared: TokenYoutubeSession,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ) {
        val label = "${video.codecFamily()}-${video.itag}"
        val holder = store.getOrCreate(
            videoId = videoId,
            userId = "live-protocol-probe-$label",
            info = prepared.info,
            audioFormat = audio,
            videoFormat = video,
            initialToken = prepared.token,
            startPump = false,
        )
        holder.markExpectedLive()
        var receivedAudio = false
        var receivedVideo = false
        var round = 0
        while (round < 6) {
            val segments = holder.session.pumpOnce(Localization("en", "US"))
            val live = holder.livePlaybackSnapshot()
            println(
                "$label round=$round requestNumber=${holder.session.requestNumber} segments=${segments.size} " +
                    "headSeq=${live?.headSequence} headMs=${live?.headTimeMs}"
            )
            segments.forEach { segment ->
                val header = segment.header
                if (!header.isInitSegment && header.sequenceNumber > 0 && segment.length > 0) {
                    if (header.itag == audio.itag) receivedAudio = true
                    if (header.itag == video.itag) receivedVideo = true
                    val format = if (header.itag == audio.itag) audio else video
                    val parts = requireNotNull(SabrLiveMediaNormalizer.split(format.mimeType.orEmpty(), segment.data)) {
                        "$label could not split live ${format.mimeType}"
                    }
                    assertTrue(parts.initialization.isNotEmpty(), "$label returned an empty initialization")
                    assertTrue(parts.media.isNotEmpty(), "$label returned empty media")
                }
                println(
                    "  itag=${header.itag} init=${header.isInitSegment} seq=${header.sequenceNumber} " +
                        "startMs=${header.startMs} durationMs=${header.durationMs} bytes=${segment.length} " +
                        "sha256=${fingerprint(segment.data)} boxes=${mp4BoxNames(segment.data)}"
                )
            }
            live?.takeIf { it.active && it.headSequence > LIVE_EDGE_SEGMENT_OFFSET }?.let {
                val targetSequence = (it.headSequence - LIVE_EDGE_SEGMENT_OFFSET).toInt()
                val targetTimeMs = (it.headTimeMs - LIVE_TARGET_LATENCY_MS).coerceAtLeast(0L)
                holder.session.streamState.setPlayerTimeMs(targetTimeMs)
                holder.session.streamState.setBufferedRangesOverride(
                    listOf(audio, video).map { format ->
                        SabrBufferedRange(
                            format.itag,
                            format.lastModified,
                            format.xtags,
                            0L,
                            targetTimeMs,
                            1,
                            targetSequence - 1,
                            1_000,
                        )
                    }
                )
            }
            round++
            if (round < 6) Thread.sleep(LIVE_EDGE_POLL_MS)
        }
        println("$label trace=${holder.session.diagnosticTrace}")
        assertTrue(receivedAudio, "$label did not retrieve live audio media")
        assertTrue(receivedVideo, "$label did not retrieve live video media")
    }

    private fun YoutubeSabrFormat.codecFamily(): String {
        val mime = mimeType.orEmpty().lowercase()
        return when {
            "avc1" in mime -> "H.264"
            "vp09" in mime || "vp9" in mime -> "VP9"
            "av01" in mime -> "AV1"
            else -> error("Unsupported probe codec for itag $itag: $mime")
        }
    }

    private fun mp4BoxNames(data: ByteArray): String {
        val names = mutableListOf<String>()
        var offset = 0
        while (offset + 8 <= data.size && names.size < 12) {
            val size = ((data[offset].toLong() and 0xff) shl 24) or
                ((data[offset + 1].toLong() and 0xff) shl 16) or
                ((data[offset + 2].toLong() and 0xff) shl 8) or
                (data[offset + 3].toLong() and 0xff)
            val type = String(data, offset + 4, 4, Charsets.US_ASCII)
            if (size < 8L || size > data.size - offset) break
            names += "$type:$size"
            offset += size.toInt()
        }
        return names.joinToString(",")
    }

    private fun fingerprint(data: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(data)
        .take(6)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val LIVE_EDGE_SEGMENT_OFFSET = 6L
        const val LIVE_TARGET_LATENCY_MS = 10_000L
    }
}
