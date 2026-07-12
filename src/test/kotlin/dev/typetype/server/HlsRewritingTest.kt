package dev.typetype.server

import dev.typetype.server.services.rewriteHlsManifest
import dev.typetype.server.services.isManifestUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HlsRewritingTest {

    @Test
    fun `googlevideo URL is rewritten through proxy`() {
        val manifest = "https://r1---sn-abc.googlevideo.com/videoplayback?id=1"
        val result = rewriteHlsManifest(manifest)
        assertTrue(result.startsWith("/proxy?url="))
    }

    @Test
    fun `multiple googlevideo URLs are all rewritten`() {
        val manifest = """
            https://r1---sn-abc.googlevideo.com/videoplayback?id=1
            https://r2---sn-xyz.googlevideo.com/videoplayback?id=2
        """.trimIndent()
        val result = rewriteHlsManifest(manifest)
        assertEquals(2, result.split("/proxy?url=").size - 1)
    }

    @Test
    fun `non-googlevideo URL is not rewritten`() {
        val manifest = "https://example.com/segment.ts"
        val result = rewriteHlsManifest(manifest)
        assertEquals(manifest, result)
    }

    @Test
    fun `empty manifest returns empty string`() {
        assertEquals("", rewriteHlsManifest(""))
    }

    @Test
    fun `signed NicoNico playlist URL is a manifest`() {
        val url = "https://delivery.domand.nicovideo.jp/media/audio.m3u8?session=abc#cookie=value"
        assertTrue(isManifestUrl(url))
    }

    @Test
    fun `manifest extension is case insensitive`() {
        assertTrue(isManifestUrl("https://example.com/media/video.M3U8?token=abc"))
    }
}
