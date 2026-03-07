package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult

interface ChannelService {
    suspend fun getChannel(url: String, nextpage: String?): ExtractionResult<ChannelResponse>
}
