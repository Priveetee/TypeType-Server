package dev.typetype.server.services

import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.models.VideoItem
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import org.schabi.newpipe.extractor.stream.StreamInfoItem

internal fun SponsorBlockSegment.toSegmentItem(): SponsorBlockSegmentItem = SponsorBlockSegmentItem(
    startTime = startTime,
    endTime = endTime,
    category = category.apiName,
    action = action.apiName,
)

internal fun List<SponsorBlockSegmentItem>.toSponsorBlockSegments(): Array<SponsorBlockSegment> =
    mapNotNull { item ->
        runCatching {
            SponsorBlockSegment(
                "",
                item.startTime,
                item.endTime,
                SponsorBlockCategory.fromApiName(item.category),
                SponsorBlockAction.fromApiName(item.action),
                0,
            )
        }.getOrNull()
    }.toTypedArray()

internal fun StreamInfoItem.toVideoItem(): VideoItem = VideoItem(
    id = url ?: "",
    title = name ?: "",
    url = url ?: "",
    thumbnailUrl = thumbnailUrl ?: "",
    uploaderName = uploaderName ?: "",
    uploaderUrl = uploaderUrl ?: "",
    uploaderAvatarUrl = uploaderAvatarUrl ?: "",
    duration = duration,
    viewCount = viewCount,
    uploadDate = textualUploadDate ?: "",
    streamType = streamType?.name?.lowercase() ?: "video",
    isShortFormContent = isShortFormContent,
    uploaderVerified = isUploaderVerified,
    shortDescription = shortDescription?.takeIf { it.isNotBlank() },
)
