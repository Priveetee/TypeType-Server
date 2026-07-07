package dev.typetype.server.services

internal enum class SabrPlaybackState {
    IDLE,
    PREPARING,
    REQUESTING,
    REPOSITIONING,
    THROTTLED,
    NETWORK_FAILED,
    TERMINAL,
    STOPPED,
}
