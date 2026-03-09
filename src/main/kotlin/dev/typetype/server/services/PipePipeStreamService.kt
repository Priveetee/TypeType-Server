package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockApiSettings
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockExtractorHelper
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo

class PipePipeStreamService(private val cache: CacheService) : StreamService {

    private val json = Json { ignoreUnknownKeys = true }

    private val sponsorBlockSettings = SponsorBlockApiSettings().apply {
        includeSponsorCategory = true
        includeIntroCategory = true
        includeOutroCategory = true
        includeInteractionCategory = true
        includeHighlightCategory = true
        includeSelfPromoCategory = true
        includeMusicCategory = true
        includePreviewCategory = true
        includeFillerCategory = true
    }

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
                    streamInfo.toStreamResponse()
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Extraction failed") }
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
                val items = json.decodeFromString<List<SponsorBlockSegmentItem>>(cached)
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
            withTimeout(15_000L) { SponsorBlockExtractorHelper.getSegments(extractor, sponsorBlockSettings) }
        }.getOrElse { emptyArray() }
        runCatching {
            val items = segments.map { it.toSegmentItem() }
            cache.set(cacheKey, json.encodeToString(items), SPONSORBLOCK_TTL_SECONDS)
        }
        return segments
    }

    private companion object {
        const val SPONSORBLOCK_TTL_SECONDS = 21600L
    }
}
