package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicHlsManifestTokenService

internal data class StreamRouteDependencies(
    val authService: AuthService?,
    val youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)?,
    val accessControlService: AccessControlService?,
    val adminSettingsService: AdminSettingsService?,
    val publicHlsManifestTokenService: PublicHlsManifestTokenService?,
    val sabrStreamContractFilter: (suspend (String, StreamResponse) -> StreamResponse)?,
)
