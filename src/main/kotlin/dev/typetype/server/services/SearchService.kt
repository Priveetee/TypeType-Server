package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse

interface SearchService {
    suspend fun search(query: String, serviceId: Int, nextpage: String? = null): ExtractionResult<SearchPageResponse>
}
