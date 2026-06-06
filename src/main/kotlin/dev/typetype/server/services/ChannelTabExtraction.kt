package dev.typetype.server.services

import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.search.filter.Filter
import org.schabi.newpipe.extractor.search.filter.FilterItem

internal fun StreamingService.channelTabExtractor(
    channelId: String,
    tab: String,
    sort: String?,
): ChannelTabExtractor {
    val contentFilter = listOf(FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, tab))
    val linkHandler = channelTabLHFactory.fromQuery(channelId, contentFilter, sort.toYouTubeChannelTabSortFilter())
    return getChannelTabExtractor(linkHandler)
}
