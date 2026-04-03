package dev.typetype.server.services

class AvatarService {

    fun normalizeEmojiCode(raw: String): String? {
        val normalized = raw.trim().uppercase()
        if (!EMOJI_CODE_REGEX.matches(normalized)) return null
        val parts = normalized.split("-")
        if (parts.size > MAX_EMOJI_PARTS) return null
        val withoutVariationSelector = parts.filterNot { it == VARIATION_SELECTOR_16 }
        if (withoutVariationSelector.isEmpty() || withoutVariationSelector.size > MAX_EMOJI_PARTS) return null
        return withoutVariationSelector.joinToString("-")
    }

    fun openMojiPath(code: String): String = "/avatar/openmoji/$code.svg"

    fun openMojiCdnUrl(code: String): String = "$OPENMOJI_CDN_BASE/$code.svg"

    fun isAllowedCustomUrl(url: String): Boolean {
        if (url.length > MAX_CUSTOM_URL_LENGTH) return false
        if (!CUSTOM_URL_REGEX.matches(url)) return false
        if (GIF_URL_REGEX.containsMatchIn(url)) return false
        return IMAGE_EXT_REGEX.containsMatchIn(url)
    }

    companion object {
        private const val OPENMOJI_CDN_BASE = "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/svg"
        private const val MAX_CUSTOM_URL_LENGTH = 2048
        private const val MAX_EMOJI_PARTS = 4
        private const val VARIATION_SELECTOR_16 = "FE0F"
        private val EMOJI_CODE_REGEX = Regex("^[0-9A-F]{2,6}(-[0-9A-F]{2,6})*$")
        private val CUSTOM_URL_REGEX = Regex("^https?://.+")
        private val GIF_URL_REGEX = Regex("\\.gif(\\?|$)", RegexOption.IGNORE_CASE)
        private val IMAGE_EXT_REGEX = Regex("\\.(png|jpe?g|webp|svg)(\\?.*)?$", RegexOption.IGNORE_CASE)
    }
}
