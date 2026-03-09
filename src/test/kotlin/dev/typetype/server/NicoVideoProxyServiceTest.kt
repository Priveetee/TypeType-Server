package dev.typetype.server

import dev.typetype.server.services.parseNicoCookie
import dev.typetype.server.services.rewriteNicoManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NicoVideoProxyServiceTest {

    @Test
    fun `parseNicoCookie extracts domand_bid from encoded fragment`() {
        val fragment = "cookie=domand_bid%3DaAbBcC123&length=1234"
        assertEquals("aAbBcC123", parseNicoCookie(fragment))
    }

    @Test
    fun `parseNicoCookie returns null when cookie key is not domand_bid`() {
        val fragment = "cookie=other_key%3DsomeValue&length=60"
        assertNull(parseNicoCookie(fragment))
    }

    @Test
    fun `parseNicoCookie returns null when fragment is empty`() {
        assertNull(parseNicoCookie(""))
    }

    @Test
    fun `parseNicoCookie returns null when cookie param is absent`() {
        assertNull(parseNicoCookie("length=120"))
    }

    @Test
    fun `rewriteNicoManifest rewrites relative segment URLs`() {
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc/video.m3u8"
        val manifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:6.0,
            seg-000001.ts
            #EXTINF:6.0,
            seg-000002.ts
        """.trimIndent()
        val result = rewriteNicoManifest(manifest, base)
        val lines = result.lines()
        val segLines = lines.filter { it.contains("/proxy/nicovideo?url=") }
        assertEquals(2, segLines.size)
        assert(segLines[0].contains("seg-000001.ts"))
        assert(segLines[1].contains("seg-000002.ts"))
    }

    @Test
    fun `rewriteNicoManifest rewrites absolute segment URLs`() {
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc/video.m3u8"
        val manifest = """
            #EXTM3U
            #EXTINF:6.0,
            https://delivery.domand.nicovideo.jp/hlsbid/abc/seg-000001.ts
        """.trimIndent()
        val result = rewriteNicoManifest(manifest, base)
        val segLine = result.lines().first { it.contains("/proxy/nicovideo?url=") }
        assert(segLine.contains("delivery.domand.nicovideo.jp"))
    }

    @Test
    fun `rewriteNicoManifest preserves comment and tag lines`() {
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc/video.m3u8"
        val manifest = "#EXTM3U\n#EXT-X-VERSION:3\nseg-001.ts"
        val result = rewriteNicoManifest(manifest, base)
        val lines = result.lines()
        assert(lines[0] == "#EXTM3U")
        assert(lines[1] == "#EXT-X-VERSION:3")
        assert(lines[2].startsWith("/proxy/nicovideo?url="))
    }
}
