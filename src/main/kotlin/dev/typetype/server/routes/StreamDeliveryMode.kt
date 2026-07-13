package dev.typetype.server.routes

import java.net.URI

internal enum class StreamDeliveryMode {
    YoutubeLegacy,
    YoutubeSabr,
    NicoNico,
    BiliBili;

    fun accepts(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return when (this) {
            YoutubeLegacy, YoutubeSabr -> host == "youtu.be" || host.endsWith(".youtube.com") || host == "youtube.com"
            NicoNico -> host == "nico.ms" || host.endsWith(".nicovideo.jp") || host == "nicovideo.jp"
            BiliBili -> host == "b23.tv" || host.endsWith(".bilibili.com") || host == "bilibili.com"
        }
    }

    fun isYoutube(): Boolean = this == YoutubeLegacy || this == YoutubeSabr

    fun isSabr(): Boolean = this == YoutubeSabr
}
