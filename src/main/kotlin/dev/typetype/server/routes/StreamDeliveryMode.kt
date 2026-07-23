package dev.typetype.server.routes

import java.net.URI

internal enum class StreamDeliveryMode {
    GenericLegacy,
    YoutubeLegacy,
    YoutubeSabr,
    NicoNico,
    BiliBili;

    fun accepts(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return when (this) {
            GenericLegacy -> true
            YoutubeLegacy, YoutubeSabr -> host == "youtu.be" || host.endsWith(".youtube.com") || host == "youtube.com"
            NicoNico -> host == "nico.ms" || host.endsWith(".nicovideo.jp") || host == "nicovideo.jp"
            BiliBili -> host == "b23.tv" || host.endsWith(".bilibili.com") || host == "bilibili.com"
        }
    }

    fun usesYoutubeSession(url: String): Boolean = when (this) {
        GenericLegacy -> runCatching { URI(url).host?.lowercase() }.getOrNull()?.let {
            it == "youtu.be" || it.endsWith(".youtube.com") || it == "youtube.com"
        } == true
        YoutubeLegacy -> true
        YoutubeSabr -> false
        NicoNico, BiliBili -> false
    }

    fun isSabr(): Boolean = this == YoutubeSabr
}
