package dev.typetype.server.services

internal const val ACCESS_MODE_UNRESTRICTED = "unrestricted"
internal const val ACCESS_MODE_ALLOW_LIST = "allow_list"
internal const val ALLOW_SCOPE_USER = "user"
internal const val ALLOW_SCOPE_GLOBAL = "global"

internal fun String.toAccessMode(): String = when (this) {
    ACCESS_MODE_ALLOW_LIST -> ACCESS_MODE_ALLOW_LIST
    else -> ACCESS_MODE_UNRESTRICTED
}
