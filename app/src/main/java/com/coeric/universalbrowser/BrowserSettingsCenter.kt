package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast

/** Lightweight, Android-Go-friendly browser settings surface. */
object BrowserSettingsCenter {
    private const val PREFS = "universal_browser_settings"
    private const val SEARCH = "search_engine"

    private val engines = linkedMapOf(
        "Google" to "https://www.google.com/search?q=",
        "Bing" to "https://www.bing.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Brave Search" to "https://search.brave.com/search?q="
    )

    fun show(activity: Activity, reload: (() -> Unit)? = null) {
        val prefs = activity.getSharedPreferences(PREFS, 0)
        val current = prefs.getString(SEARCH, "Google") ?: "Google"
        val entries = arrayOf(
            "Search engine: $current",
            "HTTPS-only: ${if (BrowserSecurityController.isHttpsOnly(activity)) "On" else "Off"}",
            "Block third-party cookies: ${if (BrowserSecurityController.blockThirdPartyCookies(activity)) "On" else "Off"}",
            "Clear browsing data",
            "Downloads",
            "Extension web stores",
            "About Universal Browser"
        )
        AlertDialog.Builder(activity).setTitle("Universal Settings").setItems(entries) { _, which ->
            when (which) {
                0 -> chooseSearch(activity)
                1 -> {
                    val enabled = !BrowserSecurityController.isHttpsOnly(activity)
                    BrowserSecurityController.setHttpsOnly(activity, enabled)
                    Toast.makeText(activity, "HTTPS-only ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    reload?.invoke()
                }
                2 -> {
                    val enabled = !BrowserSecurityController.blockThirdPartyCookies(activity)
                    BrowserSecurityController.setBlockThirdPartyCookies(activity, enabled)
                    Toast.makeText(activity, "Third-party cookie blocking ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                }
                3 -> clearData(activity)
                4 -> DownloadCenter.show(activity)
                5 -> showStores(activity)
                6 -> AlertDialog.Builder(activity).setTitle("Universal Browser").setMessage("GeckoView-based • Android Go friendly • privacy controls • WebExtensions • native AI gateway").setPositiveButton("OK", null).show()
            }
        }.setNegativeButton("Close", null).show()
    }

    private fun chooseSearch(activity: Activity) {
        val names = engines.keys.toTypedArray()
        val prefs = activity.getSharedPreferences(PREFS, 0)
        val current = prefs.getString(SEARCH, "Google") ?: "Google"
        val selected = names.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(activity).setTitle("Default search engine").setSingleChoiceItems(names, selected) { dialog, which ->
            prefs.edit().putString(SEARCH, names[which]).apply()
            dialog.dismiss()
            Toast.makeText(activity, "Search engine: ${names[which]}", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }

    fun searchUrl(activity: Activity, query: String): String {
        val prefs = activity.getSharedPreferences(PREFS, 0)
        val name = prefs.getString(SEARCH, "Google") ?: "Google"
        val base = engines[name] ?: engines.getValue("Google")
        return base + java.net.URLEncoder.encode(query, "UTF-8")
    }

    private fun clearData(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("Clear browsing data")
            .setMessage("This clears Universal's saved history, cookies and web storage. Bookmarks are kept.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                BrowserDataStore(activity).clearHistory()
                BrowserSecurityController.clearBrowsingData((activity.application as UniversalBrowserApp).getRuntime())
                try { CookieManager.getInstance().flush() } catch (_: Throwable) { }
                try { WebStorage.getInstance().deleteAllData() } catch (_: Throwable) { }
                Toast.makeText(activity, "Browsing data cleared", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showStores(activity: Activity) {
        val names = arrayOf("Firefox Add-ons", "Chrome Web Store", "Microsoft Edge Add-ons", "Opera Add-ons")
        val urls = arrayOf(
            "https://addons.mozilla.org/android/",
            "https://chromewebstore.google.com/",
            "https://microsoftedge.microsoft.com/addons/",
            "https://addons.opera.com/"
        )
        AlertDialog.Builder(activity).setTitle("Extension web stores").setItems(names) { _, which ->
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urls[which])))
        }.setNegativeButton("Close", null).show()
    }
}
