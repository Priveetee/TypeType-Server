package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs

class PipePipeChannelService : ChannelService {

    override suspend fun getChannel(url: String, nextpage: String?, sort: String?): ExtractionResult<ChannelResponse> =
        withContext(Dispatchers.IO) {
            val normalizedSort = sort?.takeIf { it.isNotBlank() }
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null
            runCatching { normalizedSort.toYouTubeChannelTabSortFilter() }
                .getOrElse { return@withContext ExtractionResult.BadRequest(it.message ?: "Invalid 'sort' parameter") }

            runCatching {
                withExtractionRetry {
                    withTimeout(30_000L) {
                        if (page == null) {
                            extractFirstPage(url, normalizedSort)
                        } else {
                            extractMorePage(url, page, normalizedSort)
                        }
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Channel extraction failed") }
            )
        }

    private fun extractFirstPage(url: String, sort: String?): ChannelResponse {
        val service = NewPipe.getServiceByUrl(url)
        val tab = url.toChannelTab(sort)
        if (tab != null) {
            val channelUrl = url.toBaseChannelUrl(tab)
            val metadata = runCatching { ChannelInfo.getInfo(channelUrl) }.getOrNull()
            val extractor = service.channelTabExtractor(channelId(channelUrl, service), tab, sort)
            extractor.fetchPage()
            return ChannelTabInfo.getInfo(extractor).toChannelTabResponse(metadata)
        }
        return ChannelInfo.getInfo(url).toChannelResponse()
    }

    private fun extractMorePage(url: String, page: Page, sort: String?): ChannelResponse {
        val service = NewPipe.getServiceByUrl(url)
        val tab = url.toChannelTab(sort)
        if (tab != null) {
            val extractor = service.channelTabExtractor(channelId(url.toBaseChannelUrl(tab), service), tab, sort)
            return extractor.getPage(page).toChannelTabResponse()
        }
        return ChannelInfo.getMoreItems(service, url, page).toChannelResponse()
    }

    private fun String.toChannelTab(sort: String?): String? {
        if (contains("/shorts", ignoreCase = true)) return ChannelTabs.SHORTS
        return if (sort != null) ChannelTabs.VIDEOS else null
    }

    private fun String.toBaseChannelUrl(tab: String): String = substringBefore("/$tab").substringBefore('?').substringBefore('#').trimEnd('/')

    private fun channelId(url: String, service: StreamingService): String = service.channelLHFactory.fromUrl(url).id
}
