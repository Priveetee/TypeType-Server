package dev.typetype.server.services

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrRandomAccessProbeTest {
    private val tokenServiceUrl: String =
        sabrProbeTokenServiceUrl()

    @Test
    fun `fetches post seek stateful responses`(): Unit = runBlocking {
        NewPipeInitializer.init()
        val videoId = sabrProbeVideoId()
        val videoItags = sabrProbeVideoItags()
        val audioItag = sabrProbeAudioItag()
        val playerTimeMs = sabrProbePlayerTimeMs()
        val timeoutMs = sabrProbeTimeoutMs()
        val store = SabrSessionStore(tokenServiceUrl = tokenServiceUrl)
        try {
            println(
                "config videoId=$videoId playerTimeMs=$playerTimeMs audioItag=$audioItag " +
                    "videoItags=${videoItags.joinToString(",")} timeoutMs=$timeoutMs " +
                    "contract=stateful-pump"
            )
            val prepared = store.fetchInfo(videoId, playerTimeMs) ?: error("SABR probe failed")
            val info = prepared.info
            val audio = requireAudioFormat(info.formats, audioItag)
            printSabrProbeFormat("audio", audio)
            for (videoItag in videoItags) {
                val video = requireVideoFormat(info.formats, videoItag)
                printSabrProbeFormat("video", video)
                val holder = store.getOrCreate(
                    videoId,
                    "sabr-random-access-video-$videoItag",
                    info,
                    audio,
                    video,
                    prepared.initialToken,
                    playerTimeMs,
                )
                assertInitData(store, holder, audio)
                assertInitData(store, holder, video)
                store.ensureWarmed(holder)
                holder.setActiveTracks(videoActive = true, audioActive = true)
                holder.setPlayerTimeMs(playerTimeMs)
                holder.mediaRequestsAt(playerTimeMs).forEach { request ->
                    println("pump target[$videoItag] ${sabrProbeRequestSummary(holder, request)}")
                }
                val startedAt = System.nanoTime()
                val segments = withTimeoutOrNull(timeoutMs) {
                    store.fetchMediaAt(holder, playerTimeMs)
                }
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                println("pump[$videoItag] elapsedMs=$elapsedMs segments=${segments?.size ?: -1}")
                segments.orEmpty().forEach { println("pump[$videoItag] ${sabrProbeSegmentHeader(it)}") }
                if (segments == null) {
                    printDirectFailure(holder, playerTimeMs, videoItag)
                }
                assertTrue(
                    segments.orEmpty().any { it.covers(video, playerTimeMs) },
                    "video[$videoItag] pump bytes",
                )
                assertTrue(
                    segments.orEmpty().any { it.covers(audio, playerTimeMs) },
                    "audio[$videoItag] pump bytes",
                )
            }
        } finally {
            store.release()
        }
    }

    private suspend fun printDirectFailure(
        holder: SabrSessionHolder,
        playerTimeMs: Long,
        videoItag: Int,
    ): Unit {
        val result = holder.pumpMutex.withLock {
            runCatchingNonCancellation {
                holder.session.fetchMediaAt(
                    playerTimeMs,
                    holder.isVideoActive(),
                    holder.isAudioActive(),
                    Localization("en", "GB"),
                )
            }
        }
        result.exceptionOrNull()?.let { error ->
            println("pump[$videoItag] direct error ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private suspend fun assertInitData(
        store: SabrSessionStore,
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): Unit {
        val data = store.fetchInitializationData(holder, format)
        println("init itag=${format.itag} bytes=${data?.size ?: -1}")
        assertTrue(data?.isNotEmpty() == true, "init bytes for itag ${format.itag}")
    }

    private fun requireAudioFormat(
        formats: List<YoutubeSabrFormat>,
        audioItag: Int,
    ): YoutubeSabrFormat =
        formats.filter { it.itag == audioItag && it.isAudio }
            .maxWithOrNull(compareBy<YoutubeSabrFormat> { it.isOriginalAudio }
                .thenBy { it.xtags.isNullOrBlank() }
                .thenBy { !it.isDrc }
                .thenBy { it.bitrate })
            ?: error("No SABR audio format for itag $audioItag")

    private fun requireVideoFormat(
        formats: List<YoutubeSabrFormat>,
        videoItag: Int,
    ): YoutubeSabrFormat =
        formats.firstOrNull { it.itag == videoItag && it.isVideo }
            ?: error("No SABR video format for itag $videoItag")

    private fun SabrMediaSegment.covers(format: YoutubeSabrFormat, playerTimeMs: Long): Boolean {
        val header = header
        if (length <= 0 || header.isInitSegment || header.itag != format.itag) return false
        val startMs = header.startMs
        val durationMs = header.durationMs
        return startMs >= 0 && durationMs > 0 &&
            playerTimeMs >= startMs && playerTimeMs < startMs + durationMs
    }
}
