package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse

interface ProxyService {
    suspend fun fetch(url: String): ExtractionResult<ProxyResponse>
}
