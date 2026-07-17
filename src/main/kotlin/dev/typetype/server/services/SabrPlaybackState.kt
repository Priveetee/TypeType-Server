package dev.typetype.server.services

internal enum class SabrPlaybackState {
    IDLE,
    PREPARING,
    REQUESTING,
    REPOSITIONING,
    WAITING_FOR_LIVE,
    THROTTLED,
    NETWORK_FAILED,
    TERMINAL,
    STOPPED,
}
