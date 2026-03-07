package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PipePipeSearchService : SearchService {

    override suspend fun search(query: String, serviceId: Int): ExtractionResult<List<VideoItem>> =
        withContext(Dispatchers.IO) {
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
                val searchInfo = SearchInfo.getInfo(service, queryHandler)
                searchInfo.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Search failed") }
            )
        }

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
        uploadDate = textualUploadDate ?: ""
    )
}
