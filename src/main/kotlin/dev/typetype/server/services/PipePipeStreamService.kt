package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.stream.StreamInfo

class PipePipeStreamService : StreamService {

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(30_000L) { StreamInfo.getInfo(url) }.toStreamResponse()
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Extraction failed") }
            )
        }
}
