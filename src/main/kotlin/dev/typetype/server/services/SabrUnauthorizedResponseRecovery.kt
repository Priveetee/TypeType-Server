package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRecoverableException

internal class SabrUnauthorizedResponseRecovery(
    private val refreshPoToken: (String) -> ByteArray?,
) {
    fun verify(holder: SabrSessionHolder): Unit {
        val response = holder.session.diagnosticTrace.substringAfterLast("response n=", missingDelimiterValue = "")
        if (!response.contains(" http=403 ")) return
        if (!holder.markUnauthorizedRefreshAttempted()) throw unauthorized()
        val token = refreshPoToken(holder.key.videoId) ?: throw unauthorized()
        holder.session.streamState.setPoToken(token)
        holder.session.addDiagnosticEvent("upstream 403 refreshed TypeType PO token")
    }

    private fun unauthorized(): SabrRecoverableException =
        SabrRecoverableException("SABR upstream unauthorized HTTP 403 after TypeType token refresh")
}
