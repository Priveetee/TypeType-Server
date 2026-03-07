package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem

interface SearchService {
    suspend fun search(query: String, serviceId: Int): ExtractionResult<List<VideoItem>>
}
