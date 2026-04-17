package dev.typetype.server.services

import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.ApplicationResponse

object AuthCookieHelpers {
    const val REFRESH_COOKIE_NAME = "refresh_token"

    fun extractRefreshToken(call: ApplicationCall): String? = call.request.cookies[REFRESH_COOKIE_NAME]

    fun setRefreshCookie(response: ApplicationResponse, token: String) {
        response.cookies.append(
            Cookie(
                name = REFRESH_COOKIE_NAME,
                value = token,
                httpOnly = true,
                secure = true,
                path = "/auth",
                maxAge = REFRESH_TTL_SECONDS.toInt(),
                encoding = CookieEncoding.RAW,
                extensions = mapOf("SameSite" to "None"),
            )
        )
        response.headers.append(HttpHeaders.AccessControlAllowCredentials, "true")
    }

    fun clearRefreshCookie(response: ApplicationResponse) {
        response.cookies.append(
            Cookie(
                name = REFRESH_COOKIE_NAME,
                value = "",
                httpOnly = true,
                secure = true,
                path = "/auth",
                maxAge = 0,
                encoding = CookieEncoding.RAW,
                extensions = mapOf("SameSite" to "None"),
            )
        )
        response.headers.append(HttpHeaders.AccessControlAllowCredentials, "true")
    }

    const val REFRESH_TTL_SECONDS = 30L * 24L * 60L * 60L
}
