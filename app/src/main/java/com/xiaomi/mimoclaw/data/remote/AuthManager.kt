package com.xiaomi.mimoclaw.data.remote

import android.content.Context
import android.webkit.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Handles Xiaomi account OAuth via WebView.
 * Flow: Open Xiaomi login page → User authenticates → Capture callback token
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Xiaomi OAuth endpoints (from the web app)
        private const val LOGIN_URL = "https://account.xiaomi.com/fe/service/login/password"
        private const val CALLBACK_HOST = "aistudio.xiaomimimo.com"
        private const val STS_PATH = "/sts"
        private const val SID = "xiaomichatbot"
    }

    /**
     * Build the Xiaomi OAuth URL with proper parameters.
     * This matches the URL pattern from the web app.
     */
    fun buildLoginUrl(): String {
        val callback = "https://$CALLBACK_HOST$STS_PATH?sign=auto&followup=https%3A%2F%2F$CALLBACK_HOST%2F%23"
        return "${LOGIN_URL}?sid=$SID&callback=${java.net.URLEncoder.encode(callback, "UTF-8")}&_locale=en"
    }

    /**
     * Check if a URL is the OAuth callback (login success).
     */
    fun isCallbackUrl(url: String): Boolean {
        return url.contains("$CALLBACK_HOST$STS_PATH") ||
               (url.contains(CALLBACK_HOST) && url.contains("#"))
    }

    /**
     * Extract cookies from WebView after login.
     * The auth token is stored in cookies by Xiaomi's system.
     */
    fun extractAuthCookies(): String? {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("aistudio.xiaomimimo.com")
        return cookies
    }

    /**
     * Parse the serviceToken from cookies.
     * Format: serviceToken=xxx; path=/; domain=.xiaomimimo.com
     */
    fun parseServiceToken(cookies: String?): String? {
        if (cookies == null) return null
        val parts = cookies.split(";").map { it.trim() }
        for (part in parts) {
            if (part.startsWith("serviceToken=")) {
                return part.removePrefix("serviceToken=")
            }
        }
        return null
    }
}
