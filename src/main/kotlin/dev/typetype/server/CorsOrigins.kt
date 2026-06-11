package dev.typetype.server

internal fun allowedOriginsFromEnv(value: String?): Set<String> = value
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotBlank() }
    .orEmpty()
    .toSet()
    .ifEmpty { error("ALLOWED_ORIGINS environment variable must be set") }

internal fun Set<String>.allowsCorsOrigin(origin: String): Boolean = "*" in this || origin in this
