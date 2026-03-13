package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockApiSettings
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockExtractorHelper
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo

private val ALL_SPONSOR_BLOCK_SETTINGS = SponsorBlockApiSettings().also {
    it.includeSponsorCategory = true
    it.includeIntroCategory = true
    it.includeOutroCategory = true
    it.includeInteractionCategory = true
    it.includeHighlightCategory = true
    it.includeSelfPromoCategory = true
    it.includeMusicCategory = true
    it.includePreviewCategory = true
    it.includeFillerCategory = true
}

internal class PipePipeStreamService(
    private val cache: CacheService,
    private val subtitleService: YouTubeSubtitleService,
    private val bilibiliRelatedService: BilibiliRelatedService,
) : StreamService {

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = NewPipe.getServiceByUrl(url)
                val extractor: StreamExtractor = service.getStreamExtractor(url)
                withTimeout(30_000L) { extractor.fetchPage() }
                coroutineScope {
                    val streamInfoDeferred = async { withTimeout(30_000L) { StreamInfo.getInfo(extractor) } }
                    val segmentsDeferred = async { resolveSegments(extractor) }
                    val streamInfo = streamInfoDeferred.await()
                    streamInfo.setSponsorBlockSegments(segmentsDeferred.await())
                    val response = streamInfo.toStreamResponse()
                    val withSubtitles = if (response.subtitles.isEmpty() && service.serviceId == 0) {
                        response.copy(subtitles = subtitleService.fetchSubtitles(streamInfo.id))
                    } else {
                        response
                    }
                    if (service.serviceId == BILIBILI_SERVICE_ID) bilibiliRelatedService.patchRelatedStreams(withSubtitles, url)
                    else withSubtitles
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { e ->
                    when (e) {
                        is GeographicRestrictionException,
                        is PaidContentException,
                        is NeedLoginException,
                        is AgeRestrictedContentException,
                        is PrivateContentException -> ExtractionResult.BadRequest(e.message ?: "Content not available")
                        else -> ExtractionResult.Failure(e.message ?: "Extraction failed")
                    }
                }
            )
        }

    private suspend fun resolveSegments(
        extractor: StreamExtractor,
    ): Array<org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment> {
        val hasSponsorBlock = extractor.getService().serviceInfo.mediaCapabilities
            .contains(MediaCapability.SPONSORBLOCK)
        if (!hasSponsorBlock) return emptyArray()
        val cacheKey = "sponsorblock:${extractor.id}"
        runCatching { cache.get(cacheKey) }.getOrNull()?.let { cached ->
            return runCatching {
                val items = CacheJson.decodeFromString<List<SponsorBlockSegmentItem>>(cached)
                items.toSponsorBlockSegments()
            }.getOrElse { fetchAndCacheSegments(extractor, cacheKey) }
        }
        return fetchAndCacheSegments(extractor, cacheKey)
    }

    private suspend fun fetchAndCacheSegments(
        extractor: StreamExtractor,
        cacheKey: String,
    ): Array<org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment> {
        val segments = runCatching {
            withTimeout(15_000L) { SponsorBlockExtractorHelper.getSegments(extractor, ALL_SPONSOR_BLOCK_SETTINGS) }
        }.getOrElse { emptyArray() }
        runCatching {
            val items = segments.map { it.toSegmentItem() }
            cache.set(cacheKey, CacheJson.encodeToString(items), SPONSORBLOCK_TTL_SECONDS)
        }
        return segments
    }

    private companion object {
        const val SPONSORBLOCK_TTL_SECONDS = 21600L
    }
}
