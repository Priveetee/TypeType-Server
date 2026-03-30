package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PipePipeChannelService : ChannelService {

    override suspend fun getChannel(url: String, nextpage: String?): ExtractionResult<ChannelResponse> =
        withContext(Dispatchers.IO) {
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null

            runCatching {
                withExtractionRetry {
                    withTimeout(30_000L) {
                        if (page == null) {
                            extractFirstPage(url)
                        } else {
                            extractMorePage(url, page)
                        }
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Channel extraction failed") }
            )
        }

    private fun extractFirstPage(url: String): ChannelResponse {
        if (isShortsTab(url)) {
            val service = NewPipe.getServiceByUrl(url)
            val linkHandler = service.channelTabLHFactory.fromUrl(url)
            return ChannelTabInfo.getInfo(service, linkHandler).toChannelTabResponse()
        }
        return ChannelInfo.getInfo(url).toChannelResponse()
    }

    private fun extractMorePage(url: String, page: org.schabi.newpipe.extractor.Page): ChannelResponse {
        val service = NewPipe.getServiceByUrl(url)
        if (isShortsTab(url)) {
            val linkHandler = service.channelTabLHFactory.fromUrl(url)
            return ChannelTabInfo.getMoreItems(service, linkHandler, page).toChannelTabResponse()
        }
        return ChannelInfo.getMoreItems(service, url, page).toChannelResponse()
    }

    private fun isShortsTab(url: String): Boolean = url.contains("/shorts", ignoreCase = true)

    private fun ChannelInfo.toChannelResponse(): ChannelResponse = ChannelResponse(
        name = name ?: "",
        description = description ?: "",
        avatarUrl = avatarUrl ?: "",
        bannerUrl = bannerUrl ?: "",
        subscriberCount = subscriberCount,
        isVerified = isVerified,
        videos = relatedItems.map { it.toVideoItem(fallbackAvatarUrl = avatarUrl ?: "") },
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

    private fun ChannelTabInfo.toChannelTabResponse(): ChannelResponse = ChannelResponse(
        name = name ?: "",
        description = "",
        avatarUrl = "",
        bannerUrl = "",
        subscriberCount = -1L,
        isVerified = false,
        videos = relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
        nextpage = nextPage?.toCursor(),
    )

    private fun InfoItemsPage<InfoItem>.toChannelTabResponse(): ChannelResponse = ChannelResponse(
        name = "",
        description = "",
        avatarUrl = "",
        bannerUrl = "",
        subscriberCount = -1L,
        isVerified = false,
        videos = items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
        nextpage = nextPage?.toCursor(),
    )
}
