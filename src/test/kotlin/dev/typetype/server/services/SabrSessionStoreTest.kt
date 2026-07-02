package dev.typetype.server.services

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrSessionStoreTest {

    private val tokenServiceUrl: String =
        System.getenv("SUBTITLE_SERVICE_URL") ?: "http://localhost:8081"

    @Test
    fun storeRoundTrip() = runBlocking {
        NewPipeInitializer.init()
        val store = SabrSessionStore(tokenServiceUrl = tokenServiceUrl)
        val videoId = System.getenv("SABR_PROBE_VIDEOS")?.split(",")?.firstOrNull()?.trim() ?: "dQw4w9WgXcQ"
        val userId = "sabr-probe-user"
        val loc = Localization("en", "GB")
        val country = ContentCountry("GB")

        println("\n========== SABR store round-trip: $videoId ==========")
        val info = YoutubeSabrProbe.fetchSabrInfo(videoId, YoutubeSabrClientProfile.WEB, loc, country)
        val audio = info.findBestAudioFormat()
        val video = info.findBestVideoFormat()
        println("probe picked: audio=${audio?.itag} video=${video?.itag}")
        check(audio != null && video != null)

        val holder = store.getOrCreate(videoId, userId, info, audio, video)
        println("session complete (cold): ${holder.session.isComplete}")

        val requests = listOf(
            SabrSegmentRequest.media(video, 1),
            SabrSegmentRequest.media(video, 2),
            SabrSegmentRequest.media(audio, 1),
            SabrSegmentRequest.media(audio, 2),
        )
        for ((i, req) in requests.withIndex()) {
            val seg = store.fetchSegment(holder, req)
            val header = seg?.header
            println(
                "  req[$i] itag=${req.format.itag} seq=${req.sequenceNumber} -> " +
                    "hit=${header != null} itag=${header?.itag} seq=${header?.sequenceNumber} " +
                    "isInit=${header?.isInitSegment} bytes=${seg?.data?.size ?: -1}"
            )
        }
        println("session complete after fetches: ${holder.session.isComplete}")

        val looked = store.lookup(videoId, userId, audio.itag, video.itag)
        println("lookup same key: ${looked === holder}")

        store.release()
    }
}
