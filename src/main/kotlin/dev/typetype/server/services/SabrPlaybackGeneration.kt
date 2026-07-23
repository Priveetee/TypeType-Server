package dev.typetype.server.services

internal fun SabrSessionHolder.nextReplacementGeneration(): Long =
    activeGeneration().let { if (it == Long.MAX_VALUE) it else it + 1L }
