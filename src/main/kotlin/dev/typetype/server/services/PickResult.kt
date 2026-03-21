package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

data class PickResult(
    val video: VideoItem?,
    val nextIndex: Int,
)
