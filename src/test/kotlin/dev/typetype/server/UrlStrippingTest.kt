package dev.typetype.server

import dev.typetype.server.services.stripTrackingParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UrlStrippingTest {

    @Test
    fun `cpn parameter is removed`() {
        val url = "https://example.com/video?itag=137&cpn=abc123&other=val"
        val result = stripTrackingParams(url)
        assertFalse(result.contains("cpn"))
        assertTrue(result.contains("itag=137"))
        assertTrue(result.contains("other=val"))
    }

    @Test
    fun `pppid parameter is removed`() {
        val url = "https://example.com/video?itag=137&pppid=xyz789&other=val"
        val result = stripTrackingParams(url)
        assertFalse(result.contains("pppid"))
        assertTrue(result.contains("itag=137"))
        assertTrue(result.contains("other=val"))
    }

    @Test
    fun `both cpn and pppid are removed`() {
        val url = "https://example.com/video?itag=137&cpn=abc&pppid=xyz"
        val result = stripTrackingParams(url)
        assertFalse(result.contains("cpn"))
        assertFalse(result.contains("pppid"))
        assertTrue(result.contains("itag=137"))
    }

    @Test
    fun `url without tracking params is unchanged`() {
        val url = "https://example.com/video?itag=137&other=val"
        val result = stripTrackingParams(url)
        assertEquals(url, result)
    }

    @Test
    fun `empty url returns empty string`() {
        assertEquals("", stripTrackingParams(""))
    }
}
