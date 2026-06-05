package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse

interface ProxyService {
    suspend fun pipe(url: String, rangeHeader: String?, domandBid: String? = null): ExtractionResult<ProxyResponse>
}
