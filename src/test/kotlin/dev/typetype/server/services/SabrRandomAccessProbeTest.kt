package dev.typetype.server.services

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrRandomAccessProbeTest {
    private val tokenServiceUrl: String =
        System.getenv("SUBTITLE_SERVICE_URL") ?: "http://localhost:8081"

    @Test
    fun `pumps post seek stateful responses`() = runBlocking {
        NewPipeInitializer.init()
        val videoId = System.getenv("SABR_PROBE_VIDEO") ?: "dQw4w9WgXcQ"
        val videoItag = System.getenv("SABR_PROBE_VIDEO_ITAG")?.toIntOrNull() ?: 137
        val audioItag = System.getenv("SABR_PROBE_AUDIO_ITAG")?.toIntOrNull() ?: 140
        val videoSequence = System.getenv("SABR_PROBE_VIDEO_SEQUENCE")?.toIntOrNull() ?: 14
        val localization = Localization("en", "GB")
        val country = ContentCountry("GB")
        val info = YoutubeSabrProbe.fetchSabrInfo(videoId, YoutubeSabrClientProfile.WEB, localization, country)
        val audio = info.formats.firstOrNull { it.itag == audioItag && it.isAudio }
            ?: info.findBestAudioFormat()
            ?: error("No SABR audio format for probe")
        val video = info.formats.firstOrNull { it.itag == videoItag && it.isVideo }
            ?: info.findBestVideoFormat()
            ?: error("No SABR video format for probe")
        val store = SabrSessionStore(tokenServiceUrl = tokenServiceUrl)
        try {
            val videoHolder = store.getOrCreate(videoId, "sabr-random-access-video", info, audio, video)
            store.ensureWarmed(videoHolder)
            val videoSegments = withTimeoutOrNull(60_000L) {
                pumpStateful(videoHolder, SabrSegmentRequest.media(video, videoSequence), localization)
            }

            assertTrue((videoSegments?.sumOf { it.length } ?: 0) > 0, "video pump bytes")
            println(
                "videoTarget=${video.itag}/$videoSequence audio=${audio.itag} " +
                    "segments=${videoSegments?.size} bytes=${videoSegments?.sumOf { it.length }}"
            )
        } finally {
            store.release()
        }
    }

    private suspend fun pumpStateful(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
    ): List<SabrMediaSegment> {
        holder.session.getCachedSegment(request)?.let { return listOf(it) }
        var segments = emptyList<SabrMediaSegment>()
        while (segments.isEmpty()) {
            holder.pumpMutex.withLock {
                holder.session.configureTargetRequest(holder, request)
                segments = holder.session.pumpOnce(localization)
                holder.session.clearTargetRequest(holder)
            }
            if (segments.isEmpty()) delay(250L)
        }
        return segments
    }
}
