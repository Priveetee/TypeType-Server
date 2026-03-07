package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchPageResponse(
    val items: List<VideoItem>,
    val nextpage: String?,
)
