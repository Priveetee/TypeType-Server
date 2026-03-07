package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PipePipeSearchService : SearchService {

    override suspend fun search(
        query: String,
        serviceId: Int,
        nextpage: String?,
    ): ExtractionResult<SearchPageResponse> =
        withContext(Dispatchers.IO) {
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null

            runCatching {
                val service = NewPipe.getService(serviceId)
                val factory = service.searchQHFactory
                val defaultContentFilter = factory.availableContentFilter
                    ?.filterGroups
                    ?.firstOrNull()
                    ?.filterItems
                    ?.firstOrNull()
                    ?.let { listOf(it) }
                    ?: emptyList<FilterItem>()
                val queryHandler = factory.fromQuery(query, defaultContentFilter, null)
                if (page == null) {
                    SearchInfo.getInfo(service, queryHandler).toPageResponse()
                } else {
                    SearchInfo.getMoreItems(service, queryHandler, page).toPageResponse()
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Search failed") }
            )
        }

    private fun SearchInfo.toPageResponse(): SearchPageResponse = SearchPageResponse(
        items = relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
        nextpage = nextPage?.toCursor(),
    )

    private fun InfoItemsPage<InfoItem>.toPageResponse(): SearchPageResponse = SearchPageResponse(
        items = items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
        nextpage = nextPage?.toCursor(),
    )

    private fun StreamInfoItem.toVideoItem(): VideoItem = VideoItem(
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
    )
}
