package dev.typetype.server.services

import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.models.VideoItem
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private val JAPANESE_DATE_REGEX = Regex("""\d{4}年\d{2}月\d{2}日 \d{2}[：:]\d{2}[：:]\d{2}""")

private fun String.extractJapaneseDate(): String = JAPANESE_DATE_REGEX.find(this)?.value ?: this

internal fun String?.toAbsoluteUrl(): String = if (this?.startsWith("http") == true) this else ""

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

internal fun StreamInfoItem.toVideoItem(fallbackAvatarUrl: String = ""): VideoItem = VideoItem(
    id = url ?: "",
    title = name ?: "",
    url = url ?: "",
    thumbnailUrl = thumbnailUrl.toAbsoluteUrl(),
    uploaderName = uploaderName ?: "",
    uploaderUrl = uploaderUrl ?: "",
    uploaderAvatarUrl = (uploaderAvatarUrl?.takeIf { it.isNotEmpty() } ?: fallbackAvatarUrl).replace("httpss://", "https://"),
    duration = duration,
    viewCount = viewCount,
    uploadDate = textualUploadDate?.extractJapaneseDate() ?: "",
    uploaded = uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: -1L,
    streamType = streamType?.name?.lowercase() ?: "video",
    isShortFormContent = isShortFormContent,
    uploaderVerified = isUploaderVerified,
    shortDescription = shortDescription?.takeIf { it.isNotBlank() },
)

internal fun VideoItem.toShortCanonicalUrl(): VideoItem {
    val id = extractYouTubeVideoId(url) ?: return this
    if (url.contains("/shorts/", ignoreCase = true)) return this
    return copy(url = "https://www.youtube.com/shorts/$id")
}

private fun extractYouTubeVideoId(url: String): String? {
    val marker = "v="
    val start = url.indexOf(marker)
    if (start == -1) return null
    val valueStart = start + marker.length
    val value = url.substring(valueStart)
    return value.substringBefore('&').substringBefore('#').takeIf { it.isNotBlank() }
}
