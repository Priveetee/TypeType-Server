package dev.typetype.server.services

import dev.typetype.server.BuildInfo
import dev.typetype.server.DEFAULT_INSTANCE_NAME
import dev.typetype.server.INSTANCE_API_VERSION
import dev.typetype.server.models.InstanceMinClientVersion
import dev.typetype.server.models.InstanceResponse
import dev.typetype.server.models.OidcPublicConfig

class InstanceService(
    private val authService: AuthService,
    private val adminSettingsService: AdminSettingsService,
    private val oidcConfigProvider: () -> OidcPublicConfig = { OidcPublicConfig(enabled = false) },
) {

    suspend fun getInstance(): InstanceResponse {
        val settings = adminSettingsService.get()
        val oidc = oidcConfigProvider()
        return InstanceResponse(
            name = settings.name.normalizeName(),
            tagline = settings.tagline.normalizeOptionalText(),
            version = BuildInfo.VERSION,
            apiVersion = INSTANCE_API_VERSION,
            registrationAllowed = settings.allowRegistration || !authService.hasUsers(),
            guestAllowed = settings.allowGuest,
            supportedServices = VALID_SERVICE_IDS.sorted(),
            minClientVersion = InstanceMinClientVersion(android = settings.minAndroidClientVersion.normalizeOptionalText()),
            logoUrl = settings.logoUrl.normalizeOptionalText(),
            bannerUrl = settings.bannerUrl.normalizeOptionalText(),
            localLoginEnabled = settings.localLoginEnabled,
            oidcEnabled = oidc.enabled,
            oidcProviderName = oidc.providerName,
            oidcAutoRedirect = oidc.enabled && settings.oidcAutoRedirect,
        )
    }

    private fun String.normalizeName(): String = trim().takeIf { it.isNotEmpty() } ?: DEFAULT_INSTANCE_NAME

    private fun String?.normalizeOptionalText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
