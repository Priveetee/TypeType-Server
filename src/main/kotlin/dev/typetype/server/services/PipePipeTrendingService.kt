package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PipePipeTrendingService : TrendingService {

    override suspend fun getTrending(serviceId: Int): ExtractionResult<List<VideoItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(30_000L) {
                    val service = NewPipe.getService(serviceId)
                    val extractor = service.kioskList.getDefaultKioskExtractor()
                        ?: return@withTimeout emptyList<VideoItem>()
                    extractor.fetchPage()
                    extractor.initialPage.items
                        .filterIsInstance<StreamInfoItem>()
                        .map { it.toVideoItem() }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Trending failed") }
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
