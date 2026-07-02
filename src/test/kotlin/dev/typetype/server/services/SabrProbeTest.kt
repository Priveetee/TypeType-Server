package dev.typetype.server.services

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrProbeTest {

    private val tokenServiceUrl: String =
        System.getenv("SUBTITLE_SERVICE_URL") ?: "http://localhost:8081"

    private val probeVideos: List<String> =
        (System.getenv("SABR_PROBE_VIDEOS") ?: "dQw4w9WgXcQ,9bZkp7q19f0,aqz-KE-bpKQ,jNQXAC9IVRw")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun probeSabr() {
        NewPipeInitializer.init()
        val loc = Localization("en", "GB")
        val country = ContentCountry("GB")
        val provider = TypetypeTokenSabrPoTokenProvider(tokenServiceUrl)
        val profile = YoutubeSabrClientProfile.WEB

        for (videoId in probeVideos) {
            println("\n========== SABR probe: $videoId ==========")
            try {
                val info = YoutubeSabrProbe.fetchSabrInfo(videoId, profile, loc, country)
                println("serverAbrStreamingUrl present: ${!info.serverAbrStreamingUrl.isNullOrEmpty()}")
                println("videoPlaybackUstreamerConfig present: ${!info.videoPlaybackUstreamerConfig.isNullOrEmpty()}")
                println("--- formats (itag | A/V | height×width | bitrate | mime | audioTrack | approxDurMs) ---")
                info.formats.forEach { f ->
                    val kind = when {
                        f.isAudio -> "A"
                        f.isVideo -> "V"
                        else -> "?"
                    }
                    println(
                        "  ${f.itag} | $kind | ${f.height}×${f.width} | br=${f.bitrate} | " +
                            "${f.mimeType} | track=${f.audioTrackId} | dur≈${f.approxDurationMs}ms"
                    )
                }
                val hasAvc = info.formats.any { it.itag == 137 }
                println("AVC itag 137 present: $hasAvc")

                val audio = info.findBestAudioFormat()
                val video = info.findBestVideoFormat()
                println("selected pair: audio=${audio?.itag} video=${video?.itag}")
                if (audio == null || video == null) {
                    println("no usable audio/video pair — skipping session")
                    continue
                }

                val session = YoutubeSabrSession(info, audio, video, provider)
                println("--- pumpOnce #1 ---")
                val segments = session.pumpOnce(loc)
                println("segments returned: ${segments.size}")
                segments.take(8).forEach { s ->
                    val h = s.header
                    println(
                        "  itag=${h.itag} seq=${h.sequenceNumber} isInit=${h.isInitSegment} " +
                            "dur=${h.durationMs}ms start=${h.startMs}ms bytes=${s.data.size}"
                    )
                }
                println("session complete: ${session.isComplete}")
            } catch (e: Exception) {
                println("FAILED on $videoId: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
