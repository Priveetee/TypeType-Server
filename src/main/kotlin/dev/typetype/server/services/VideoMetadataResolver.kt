package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.PlaylistVideoItem

class VideoMetadataResolver(private val streamService: StreamService) {
    suspend fun enrichPlaylistVideos(videos: List<PlaylistVideoItem>): List<PlaylistVideoItem> {
        val metadata = resolve(videos.filter(::shouldEnrich).map { it.url })
        return videos.map { video -> metadata[video.url]?.toPlaylistVideo(video) ?: video }
    }

    suspend fun enrichPlaylistItems(items: Map<String, List<PlaylistVideoItem>>): Map<String, List<PlaylistVideoItem>> {
        val metadata = resolve(items.values.flatten().filter(::shouldEnrich).map { it.url })
        return items.mapValues { (_, videos) -> videos.map { video -> metadata[video.url]?.toPlaylistVideo(video) ?: video } }
    }

    suspend fun enrichFavorites(items: List<FavoriteItem>): List<FavoriteItem> {
        val distinct = items.distinctBy { it.videoUrl }
        val metadata = resolve(distinct.map { it.videoUrl })
        return distinct.map { item -> metadata[item.videoUrl]?.toFavorite(item) ?: item.withYoutubeFallbackTitle() }
    }

    suspend fun resolve(urls: Collection<String>): Map<String, VideoMetadataItem> {
        val resolved = mutableMapOf<String, VideoMetadataItem>()
        urls.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { url ->
            when (val result = streamService.getStreamInfo(url)) {
                is ExtractionResult.Success -> resolved[url] = result.data.toMetadata(url)
                is ExtractionResult.BadRequest -> Unit
                is ExtractionResult.Failure -> Unit
            }
        }
        return resolved
    }

    private fun shouldEnrich(video: PlaylistVideoItem): Boolean =
        video.title.startsWith(FALLBACK_TITLE_PREFIX) || video.thumbnail.startsWith(YOUTUBE_THUMB_PREFIX) ||
            video.duration <= 0L || video.channelName.isBlank() || video.channelUrl.isBlank()

    private fun VideoMetadataItem.toPlaylistVideo(video: PlaylistVideoItem): PlaylistVideoItem = video.copy(
        title = title.ifBlank { video.title },
        thumbnail = thumbnail.ifBlank { video.thumbnail },
        duration = duration.takeIf { it > 0L } ?: video.duration,
        channelName = channelName.ifBlank { video.channelName },
        channelUrl = channelUrl.ifBlank { video.channelUrl },
        channelAvatar = channelAvatar.ifBlank { video.channelAvatar },
        viewCount = viewCount.takeIf { it > 0L } ?: video.viewCount,
        publishedAt = publishedAt.takeIf { it > 0L } ?: video.publishedAt,
    )

    private fun VideoMetadataItem.toFavorite(item: FavoriteItem): FavoriteItem = FavoriteItem(
        videoUrl = item.videoUrl,
        favoritedAt = item.favoritedAt,
        title = title,
        thumbnail = thumbnail,
        duration = duration,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatar = channelAvatar,
        viewCount = viewCount,
        publishedAt = publishedAt,
    )

    private fun dev.typetype.server.models.StreamResponse.toMetadata(url: String): VideoMetadataItem = VideoMetadataItem(
        url = url,
        title = title,
        thumbnail = thumbnailUrl,
        duration = duration,
        channelName = uploaderName,
        channelUrl = uploaderUrl,
        channelAvatar = uploaderAvatarUrl,
        viewCount = viewCount,
        publishedAt = publishedAt ?: uploaded,
    )

    private companion object {
        const val FALLBACK_TITLE_PREFIX = "YouTube video "
        const val YOUTUBE_THUMB_PREFIX = "https://i.ytimg.com/vi/"
    }
}
