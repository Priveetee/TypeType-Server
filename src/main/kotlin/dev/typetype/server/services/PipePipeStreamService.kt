package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.NewPipe
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
                val streamInfo = withTimeout(30_000L) { StreamInfo.getInfo(extractor) }
                streamInfo.setSponsorBlockSegments(resolveSegments(extractor))
                streamInfo.toStreamResponse()
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Extraction failed") }
            )
        }

    private suspend fun resolveSegments(
        extractor: StreamExtractor,
    ): Array<org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment> {
        val videoId = extractor.id
        val cacheKey = "sponsorblock:$videoId"
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
            SponsorBlockExtractorHelper.getSegments(extractor, sponsorBlockSettings)
        }.getOrElse { emptyArray() }
        runCatching {
            val items = segments.map { it.toSegmentItem() }
            cache.set(cacheKey, json.encodeToString(items), SPONSORBLOCK_TTL_SECONDS)
        }
        return segments
    }

    private companion object {
        const val SPONSORBLOCK_TTL_SECONDS = 1800L
    }
}
