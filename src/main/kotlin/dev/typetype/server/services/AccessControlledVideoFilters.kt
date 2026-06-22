package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

internal fun List<VideoItem>.filterAllowed(profile: AccessControlProfile): List<VideoItem> =
    filter { profile.allowsUploader(url = it.uploaderUrl, name = it.uploaderName) }

internal fun VideoItem.isAllowedBy(profile: AccessControlProfile): Boolean =
    profile.allowsUploader(url = uploaderUrl, name = uploaderName)
