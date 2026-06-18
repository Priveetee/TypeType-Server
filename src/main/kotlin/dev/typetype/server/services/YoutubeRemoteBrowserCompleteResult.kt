package dev.typetype.server.services

sealed interface YoutubeRemoteBrowserCompleteResult {
    data object Completed : YoutubeRemoteBrowserCompleteResult
    data object Unauthorized : YoutubeRemoteBrowserCompleteResult
    data object NotFound : YoutubeRemoteBrowserCompleteResult
    data object InvalidPayload : YoutubeRemoteBrowserCompleteResult
    data object InvalidCredentials : YoutubeRemoteBrowserCompleteResult
    data object Unavailable : YoutubeRemoteBrowserCompleteResult
}
