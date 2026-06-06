package dev.typetype.server.services

internal object PublicCachePolicy {
    fun trendingTtl(serviceId: Int): Long = when (serviceId) {
        BILIBILI_SERVICE_ID, NICONICO_SERVICE_ID -> 600L
        YOUTUBE_SERVICE_ID -> 1_800L
        else -> 3_600L
    }

    fun suggestionTtl(serviceId: Int): Long = when (serviceId) {
        BILIBILI_SERVICE_ID, NICONICO_SERVICE_ID -> 900L
        else -> 1_800L
    }

    fun searchTtl(serviceId: Int, nextpage: String?): Long {
        if (nextpage != null) return 300L
        return when (serviceId) {
            BILIBILI_SERVICE_ID, NICONICO_SERVICE_ID -> 300L
            YOUTUBE_SERVICE_ID -> 600L
            else -> 900L
        }
    }

    fun channelTtl(url: String, nextpage: String?, sort: String?): Long = when {
        url.contains("/search", ignoreCase = true) -> 600L
        nextpage != null -> 1_800L
        url.contains("/shorts", ignoreCase = true) -> 900L
        sort.equals("latest", ignoreCase = true) -> 900L
        else -> 3_600L
    }

    fun commentsTtl(url: String, nextpage: String?): Long {
        if (nextpage != null) return 600L
        return when (url.serviceHint()) {
            BILIBILI_SERVICE_ID, NICONICO_SERVICE_ID -> 300L
            else -> 180L
        }
    }
}

private fun String.serviceHint(): Int? = when {
    contains("bilibili.com", ignoreCase = true) -> BILIBILI_SERVICE_ID
    contains("nicovideo.jp", ignoreCase = true) -> NICONICO_SERVICE_ID
    contains("youtube.com", ignoreCase = true) || contains("youtu.be", ignoreCase = true) -> YOUTUBE_SERVICE_ID
    else -> null
}
