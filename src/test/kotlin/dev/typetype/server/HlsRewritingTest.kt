package dev.typetype.server

import dev.typetype.server.services.rewriteHlsManifest
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
}
