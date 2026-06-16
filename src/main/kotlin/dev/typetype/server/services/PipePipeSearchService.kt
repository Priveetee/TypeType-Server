package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchFiltersResponse
import dev.typetype.server.models.SearchPageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.search.SearchInfo

class PipePipeSearchService : SearchService {

    override suspend fun search(
        query: String,
        serviceId: Int,
        nextpage: String?,
        contentFilter: String?,
        sortFilter: String?,
    ): ExtractionResult<SearchPageResponse> =
        withContext(Dispatchers.IO) {
            if (serviceId == YOUTUBE_SERVICE_ID && sortFilter != null) {
                return@withContext ExtractionResult.BadRequest("Sort filters are unavailable for YouTube")
            }

            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null

            runCatching {
                withExtractionRetry {
                    withTimeout(30_000L) {
                        val service = NewPipe.getService(serviceId)
                        val factory = service.searchQHFactory
                        val selectedContentFilter = if (contentFilter == null) {
                            factory.availableContentFilter.defaultSearchFilter()
                        } else {
                            factory.availableContentFilter.findSearchFilter(contentFilter)
                        }
                        val selectedSortFilter = factory.availableSortFilter.findSearchFilter(sortFilter)
                        val queryHandler = factory.fromQuery(
                            query,
                            selectedContentFilter.ifEmpty { null },
                            selectedSortFilter.ifEmpty { null },
                        )
                        val contentKind = contentFilter.toSearchContentKind(selectedContentFilter)
                        if (page == null) {
                            SearchInfo.getInfo(service, queryHandler).toSearchPageResponse().filteredBy(contentKind)
                        } else {
                            SearchInfo.getMoreItems(service, queryHandler, page).toSearchPageResponse().filteredBy(contentKind)
                        }
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Search failed") }
            )
        }

    override suspend fun filters(serviceId: Int): ExtractionResult<SearchFiltersResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val factory = NewPipe.getService(serviceId).searchQHFactory
                SearchFiltersResponse(
                    contentFilters = factory.availableContentFilter.toSearchFilterOptions(),
                    sortFilters = if (serviceId == YOUTUBE_SERVICE_ID) {
                        emptyList()
                    } else {
                        factory.availableSortFilter.toSearchFilterOptions()
                    },
                )
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Search filters failed") },
            )
        }

    private companion object {
        const val YOUTUBE_SERVICE_ID = 0
    }
}
