package dev.typetype.server.services

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypetypeTokenYoutubeSessionClientTest {
    @Test
    fun `fetches mweb live manifest`() = runTest {
        var requestedVideoId: String? = null
        var requestedClient: String? = null
        val httpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requestedVideoId = chain.request().url.queryParameter("videoId")
            requestedClient = chain.request().url.queryParameter("client")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"hlsManifestUrl":"https://example.com/live.m3u8"}""".toResponseBody())
                .build()
        }).build()
        val client = TypetypeTokenYoutubeSessionClient("https://token.example/base/", httpClient)

        val result = client.fetchHlsManifestUrl("a b")

        assertEquals("https://example.com/live.m3u8", result)
        assertEquals("a b", requestedVideoId)
        assertEquals("MWEB", requestedClient)
    }

    @Test
    fun `reconstructs pipepipe sabr info from token session`() = runTest {
        val body = """
            {
              "visitorData":"visitor-data",
              "serverAbrStreamingUrl":"https://example.com/sabr",
              "videoPlaybackUstreamerConfig":"ustreamer-config",
              "adaptiveFormats":[
                {
                  "itag":137,
                  "lastModified":"1700000000000",
                  "mimeType":"video/mp4; codecs=\"avc1.640028\"",
                  "qualityLabel":"1080p",
                  "width":1920,
                  "height":1080,
                  "bitrate":4000000,
                  "contentLength":1000000,
                  "approxDurationMs":60000,
                  "url":"https://example.com/video",
                  "initRange":{"start":0,"end":741},
                  "indexRange":{"start":742,"end":9281}
                },
                {
                  "itag":140,
                  "lastModified":"1700000000001",
                  "mimeType":"audio/mp4; codecs=\"mp4a.40.2\"",
                  "audioQuality":"AUDIO_QUALITY_MEDIUM",
                  "audioTrack":{"id":"fr-FR.4","displayName":"French (original)","audioIsDefault":true},
                  "bitrate":129000,
                  "contentLength":100000,
                  "approxDurationMs":60000,
                  "url":"https://example.com/audio",
                  "initRange":{"start":0,"end":722},
                  "indexRange":{"start":723,"end":5038}
                }
              ]
            }
        """.trimIndent()
        val httpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
        }).build()
        val client = TypetypeTokenYoutubeSessionClient("https://token.example", httpClient)

        val info = client.fetchSabrInfo("video-id")

        assertNotNull(info)
        assertEquals("video-id", info?.videoId)
        assertEquals("https://example.com/sabr", info?.serverAbrStreamingUrl)
        assertEquals(2, info?.formats?.size)
        assertTrue(info?.formats?.any { it.itag == 137 && it.initRangeEnd == 9281L } == true)
        assertTrue(info?.formats?.any { it.itag == 140 && it.audioTrackId == "fr-FR.4" } == true)
    }
}
