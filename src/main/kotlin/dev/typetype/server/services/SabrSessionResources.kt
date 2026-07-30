package dev.typetype.server.services

internal fun SabrSessionHolder.releaseResources() {
    clearSegmentDemands()
    clearInFlightSegmentDemand()
    SabrPlaybackDiagnostics.clear(this)
    session.clearCache()
}
