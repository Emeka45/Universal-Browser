package com.coeric.universalbrowser

import android.content.Context
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime

/** Central privacy/data controls backed by GeckoView rather than Android WebView APIs. */
object BrowserSecurityController {
    private const val PREFS = "universal_security"
    private const val BLOCK_THIRD_PARTY_COOKIES = "block_third_party_cookies"
    private const val HTTPS_ONLY = "https_only"

    fun isHttpsOnly(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(HTTPS_ONLY, true)

    fun setHttpsOnly(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(HTTPS_ONLY, enabled).apply()

    fun setBlockThirdPartyCookies(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(BLOCK_THIRD_PARTY_COOKIES, enabled).apply()

    fun blockThirdPartyCookies(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(BLOCK_THIRD_PARTY_COOKIES, false)

    /** Apply the persisted browser policy to the actual Gecko runtime. */
    fun applyRuntimePolicy(context: Context, runtime: GeckoRuntime) {
        val cookies = if (blockThirdPartyCookies(context)) {
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
        } else {
            ContentBlocking.CookieBehavior.ACCEPT_ALL
        }
        runtime.settings.contentBlocking.setCookieBehavior(cookies)
        runtime.settings.contentBlocking.setCookieBehaviorPrivateMode(cookies)
    }

    fun shouldAllowNavigation(context: Context, url: String): Boolean {
        if (!isHttpsOnly(context)) return true
        val lower = url.trim().lowercase()
        return lower.startsWith("https://") ||
            lower.startsWith("about:") ||
            lower.startsWith("resource:") ||
            lower.startsWith("file:")
    }

    /** Compatibility entry point retained for existing session setup code. */
    fun applySessionPolicy(context: Context, session: org.mozilla.geckoview.GeckoSession) {
        // Cookie policy belongs to GeckoRuntime, not Android WebView. Session-level
        // navigation remains protected by shouldAllowNavigation().
    }

    /** Clear Gecko's actual browser storage. Call after closing/suspending active sessions. */
    fun clearBrowsingData(runtime: GeckoRuntime, onComplete: (Boolean) -> Unit = {}) {
        runtime.storageController.clearData(
            org.mozilla.geckoview.StorageController.ClearFlags.SITE_DATA or
                org.mozilla.geckoview.StorageController.ClearFlags.AUTH_SESSIONS
        ).accept(
            { onComplete(true) },
            { onComplete(false) }
        )
    }
}
