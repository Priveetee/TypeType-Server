package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchFiltersResponse
import dev.typetype.server.models.SearchPageResponse

class YoutubeScopedSearchService(private val delegate: SearchService) : SearchService {
    override suspend fun search(
        query: String,
        serviceId: Int,
        nextpage: String?,
        contentFilter: String?,
        sortFilter: String?,
    ): ExtractionResult<SearchPageResponse> =
        if (serviceId == YOUTUBE_SERVICE_ID) {
            YoutubeSessionTokenScope.withoutCredentials {
                delegate.search(query, serviceId, nextpage, contentFilter, sortFilter)
            }
        } else {
            delegate.search(query, serviceId, nextpage, contentFilter, sortFilter)
        }

    override suspend fun filters(serviceId: Int): ExtractionResult<SearchFiltersResponse> =
        delegate.filters(serviceId)
}
