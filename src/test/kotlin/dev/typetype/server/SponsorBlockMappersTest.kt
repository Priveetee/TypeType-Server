package dev.typetype.server

import dev.typetype.server.services.toSponsorBlockSegments
import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.services.toSegmentItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment

class SponsorBlockMappersTest {

    @Test
    fun `toSegmentItem maps category and action api names`() {
        val segment = SponsorBlockSegment(
            "uuid-1", 1000.0, 5000.0,
            SponsorBlockCategory.SPONSOR, SponsorBlockAction.SKIP, 0,
        )
        val item = segment.toSegmentItem()
        assertEquals("sponsor", item.category)
        assertEquals("skip", item.action)
        assertEquals(1000.0, item.startTime)
        assertEquals(5000.0, item.endTime)
    }

    @Test
    fun `toSponsorBlockSegments roundtrip preserves times and category`() {
        val items = listOf(
            SponsorBlockSegmentItem(startTime = 2000.0, endTime = 8000.0, category = "outro", action = "skip"),
        )
        val segments = items.toSponsorBlockSegments()
        assertEquals(1, segments.size)
        assertEquals(2000.0, segments[0].startTime)
        assertEquals(8000.0, segments[0].endTime)
        assertEquals(SponsorBlockCategory.OUTRO, segments[0].category)
        assertEquals(SponsorBlockAction.SKIP, segments[0].action)
    }

    @Test
    fun `toSponsorBlockSegments skips unknown category`() {
        val items = listOf(
            SponsorBlockSegmentItem(startTime = 0.0, endTime = 1.0, category = "unknown_cat", action = "skip"),
        )
        val segments = items.toSponsorBlockSegments()
        assertEquals(0, segments.size)
    }
}
