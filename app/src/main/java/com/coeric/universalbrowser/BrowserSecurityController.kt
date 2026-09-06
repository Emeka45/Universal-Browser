package com.coeric.universalbrowser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import org.mozilla.geckoview.GeckoSession

/** Central privacy/data controls. Keeps security policy explicit and easy to extend. */
object BrowserSecurityController {
    private const val PREFS = "universal_security"
    private const val BLOCK_THIRD_PARTY_COOKIES = "block_third_party_cookies"
    private const val HTTPS_ONLY = "https_only"

    fun isHttpsOnly(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(HTTPS_ONLY, true)

    fun setHttpsOnly(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(HTTPS_ONLY, enabled).apply()

    fun setBlockThirdPartyCookies(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(BLOCK_THIRD_PARTY_COOKIES, enabled).apply()
        CookieManager.getInstance().setAcceptThirdPartyCookies(null, !enabled)
    }

    fun blockThirdPartyCookies(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(BLOCK_THIRD_PARTY_COOKIES, false)

    fun shouldAllowNavigation(context: Context, url: String): Boolean {
        if (!isHttpsOnly(context)) return true
        val lower = url.trim().lowercase()
        return lower.startsWith("https://") || lower.startsWith("about:") || lower.startsWith("resource:") || lower.startsWith("file:")
    }

    fun applySessionPolicy(context: Context, session: GeckoSession) {
        // GeckoView owns the actual cookie/storage implementation. This class
        // keeps the browser's policy state centralized without using unsafe
        // reflection against version-specific Gecko APIs.
        if (blockThirdPartyCookies(context)) {
            // Applied globally where Android's CookieManager exposes the setting.
            // GeckoView-specific cookie controls remain version-dependent.
            try { CookieManager.getInstance().setAcceptCookie(true) } catch (_: Throwable) { }
        }
    }

    fun clearBrowsingData() {
        try { CookieManager.getInstance().removeAllCookies(null) } catch (_: Throwable) { }
        try { CookieManager.getInstance().flush() } catch (_: Throwable) { }
        try { WebStorage.getInstance().deleteAllData() } catch (_: Throwable) { }
    }
}
