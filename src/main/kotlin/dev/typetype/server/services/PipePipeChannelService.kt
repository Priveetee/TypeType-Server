package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PipePipeChannelService : ChannelService {

    override suspend fun getChannel(url: String, nextpage: String?): ExtractionResult<ChannelResponse> =
        withContext(Dispatchers.IO) {
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null

            runCatching {
                withTimeout(30_000L) {
                    if (page == null) {
                        ChannelInfo.getInfo(url).toChannelResponse()
                    } else {
                        val service = NewPipe.getServiceByUrl(url)
                        ChannelInfo.getMoreItems(service, url, page).toChannelResponse()
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Channel extraction failed") }
            )
        }

    private fun ChannelInfo.toChannelResponse(): ChannelResponse = ChannelResponse(
        name = name ?: "",
        description = description ?: "",
        avatarUrl = avatarUrl ?: "",
        bannerUrl = bannerUrl ?: "",
        subscriberCount = subscriberCount,
        isVerified = isVerified,
        videos = relatedItems.map { it.toVideoItem() },
        nextpage = nextPage?.toCursor(),
    )

    private fun InfoItemsPage<StreamInfoItem>.toChannelResponse(): ChannelResponse = ChannelResponse(
        name = "",
        description = "",
        avatarUrl = "",
        bannerUrl = "",
        subscriberCount = -1L,
        isVerified = false,
        videos = items.map { it.toVideoItem() },
        nextpage = nextPage?.toCursor(),
    )
}
