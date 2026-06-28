package dev.typetype.server.services

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun isManifestUrl(url: String): Boolean {
    if (!url.startsWith("http")) return false
    if (url.contains("/file/seg.ts")) return false
    return url.contains("manifest.googlevideo.com") || url.endsWith(".m3u8")
}

internal fun rewriteYouTubeHlsManifest(
    manifest: String,
    mapManifestUrl: (String) -> String = ::toHlsProxyUrl,
): String {
    val uriAttr = Regex("""URI="([^"]+)"""")
    return manifest.lines().joinToString("\n") { line ->
        val t = line.trim()
        when {
            t.isBlank() -> line
            t.startsWith("#") -> uriAttr.replace(t) { mr -> """URI="${toHlsUrl(mr.groupValues[1], mapManifestUrl)}""" }
            else -> toHlsUrl(t, mapManifestUrl)
        }
    }
}

internal fun toHlsProxyUrl(url: String): String {
    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8)
    return if (isManifestUrl(url)) "hls-manifest?url=$encoded" else "../proxy?url=$encoded"
}

private fun toHlsUrl(url: String, mapManifestUrl: (String) -> String): String =
    if (isManifestUrl(url)) mapManifestUrl(url) else toHlsProxyUrl(url)
