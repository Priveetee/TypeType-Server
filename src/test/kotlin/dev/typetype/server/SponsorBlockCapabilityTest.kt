package dev.typetype.server

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability

class SponsorBlockCapabilityTest {

    @Test
    fun `YouTube has SPONSORBLOCK capability`() {
        val caps = NewPipe.getService(0).serviceInfo.mediaCapabilities
        assertTrue(caps.contains(MediaCapability.SPONSORBLOCK))
    }

    @Test
    fun `NicoNico does not have SPONSORBLOCK capability`() {
        val caps = NewPipe.getService(6).serviceInfo.mediaCapabilities
        assertFalse(caps.contains(MediaCapability.SPONSORBLOCK))
    }

    @Test
    fun `BiliBili has SPONSORBLOCK capability`() {
        val caps = NewPipe.getService(5).serviceInfo.mediaCapabilities
        assertTrue(caps.contains(MediaCapability.SPONSORBLOCK))
    }
}
