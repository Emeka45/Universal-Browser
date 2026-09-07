package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.widget.EditText
import android.widget.Toast
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/** Central browser power tools: desktop mode, zoom, reader, translation, sharing and privacy. */
object BrowserPowerCenter {
    private const val PREFS = "universal_browser_power"
    private const val DESKTOP = "desktop_mode"
    private const val TEXT_SCALE = "text_scale"

    fun show(activity: Activity, sessionProvider: () -> GeckoSession?, currentUrlProvider: () -> String, reload: () -> Unit) {
        val session = sessionProvider()
        if (session == null) { Toast.makeText(activity, "Open a page first", Toast.LENGTH_SHORT).show(); return }
        val items = arrayOf("Desktop site", "Page zoom", "Reader mode", "Translate page", "Find in page", "Share page", "Save offline page", "Save page as PDF", "Screenshot page", "Print page", "Bookmark page", "Scan QR code", "Generate QR code", "Site controls", "Privacy & protection", "Site permissions", "Extensions & web stores", "Downloads", "Settings")
        AlertDialog.Builder(activity).setTitle("Universal tools").setItems(items) { _, which ->
            when (which) {
                0 -> toggleDesktop(activity, session, reload)
                1 -> showZoom(activity, session, reload)
                2 -> BrowserAdvancedTools.openReaderMode(activity, session, currentUrlProvider())
                3 -> BrowserAdvancedTools.translatePage(activity, session)
                4 -> findInPage(activity, session)
                5 -> share(activity, currentUrlProvider())
                6 -> BrowserAdvancedTools.saveOfflineSnapshot(activity, session)
                7 -> BrowserFeatureCenter.savePageAsPdf(activity, session)
                8 -> BrowserFeatureCenter.captureVisiblePage(activity, session)
                9 -> BrowserFeatureCenter.printPage(activity, session)
                10 -> bookmark(activity, currentUrlProvider())
                11 -> BrowserAdvancedTools.showQrScanner(activity)
                12 -> BrowserAdvancedTools.showQrGenerator(activity, currentUrlProvider())
                13 -> BrowserFeatureCenter.showSiteControls(activity, session, currentUrlProvider())
                14 -> showPrivacy(activity, session)
                15 -> showSitePermissions(activity)
                16 -> BrowserExtensionCenter.show(activity)
                17 -> DownloadCenter.show(activity)
                18 -> BrowserSettingsCenter.show(activity, reload)
            }
        }.setNegativeButton("Close", null).show()
    }

    fun applyPreferences(activity: Activity, session: GeckoSession) {
        val prefs = activity.getSharedPreferences(PREFS, 0)
        val desktop = prefs.getBoolean(DESKTOP, false)
        session.settings.setUserAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        session.settings.setViewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        BrowserSecurityController.applySessionPolicy(activity, session)
        BrowserPermissionController.attach(activity, session)
    }

    private fun toggleDesktop(activity: Activity, session: GeckoSession, reload: () -> Unit) {
        val prefs = activity.getSharedPreferences(PREFS, 0); val enabled = !prefs.getBoolean(DESKTOP, false)
        prefs.edit().putBoolean(DESKTOP, enabled).apply()
        session.settings.setUserAgentMode(if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        session.settings.setViewportMode(if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        reload(); Toast.makeText(activity, if (enabled) "Desktop site enabled" else "Mobile site enabled", Toast.LENGTH_SHORT).show()
    }

    private fun showZoom(activity: Activity, session: GeckoSession, reload: () -> Unit) {
        val values = arrayOf("50%", "60%", "70%", "80%", "90%", "100%", "110%", "125%", "150%", "175%", "200%", "225%", "250%")
        val prefs = activity.getSharedPreferences(PREFS, 0); val current = prefs.getFloat(TEXT_SCALE, 1f)
        val selected = values.indexOfFirst { kotlin.math.abs(it.dropLast(1).toFloat() / 100f - current) < .01f }.let { if (it < 0) 5 else it }
        AlertDialog.Builder(activity).setTitle("Page zoom").setSingleChoiceItems(values, selected) { dialog, which ->
            val factor = values[which].dropLast(1).toFloat() / 100f; prefs.edit().putFloat(TEXT_SCALE, factor).apply(); dialog.dismiss(); reload()
        }.setNeutralButton("Reset 100%") { _, _ -> prefs.edit().putFloat(TEXT_SCALE, 1f).apply(); reload() }.setNegativeButton("Cancel", null).show()
    }

    private fun findInPage(activity: Activity, session: GeckoSession) {
        val input = EditText(activity).apply { hint = "Find text on this page" }
        AlertDialog.Builder(activity).setTitle("Find in page").setView(input).setNegativeButton("Close", null).setPositiveButton("Find") { _, _ ->
            val term = input.text.toString().trim(); if (term.isBlank()) return@setPositiveButton
            session.finder.find(term, 0).accept({ result -> Toast.makeText(activity, if (result?.found == true) "Found match ${result.current}" else "No match found", Toast.LENGTH_SHORT).show() }, { error -> Toast.makeText(activity, "Find failed: ${error?.message ?: "unknown error"}", Toast.LENGTH_SHORT).show() })
        }.show()
    }

    private fun share(activity: Activity, url: String) {
        if (url.isBlank()) return
        try { activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url); putExtra(Intent.EXTRA_TITLE, "Share from Universal Browser") }, "Share page")) }
        catch (_: Throwable) { Toast.makeText(activity, "No app available to share this page", Toast.LENGTH_SHORT).show() }
    }

    private fun bookmark(activity: Activity, url: String) {
        if (url.isBlank()) return
        BrowserDataStore(activity).addBookmark(url, try { Uri.parse(url).host ?: url } catch (_: Throwable) { url }); Toast.makeText(activity, "Bookmarked", Toast.LENGTH_SHORT).show()
    }

    private fun showPrivacy(activity: Activity, session: GeckoSession) {
        val settings = session.settings
        val items = arrayOf("Tracking protection: Enabled by browser security policy", "JavaScript: ${if (settings.allowJavascript) "On" else "Off"}", "HTTPS-only: ${if (BrowserSecurityController.isHttpsOnly(activity)) "On" else "Off"}", "Block third-party cookies: ${if (BrowserSecurityController.blockThirdPartyCookies(activity)) "On" else "Off"}", "Clear history", "Clear cookies", "Clear site data")
        AlertDialog.Builder(activity).setTitle("Privacy & protection").setItems(items) { _, which ->
            when (which) {
                0 -> Toast.makeText(activity, "Tracking protection is controlled by the browser security policy", Toast.LENGTH_SHORT).show()
                1 -> settings.allowJavascript = !settings.allowJavascript
                2 -> BrowserSecurityController.setHttpsOnly(activity, !BrowserSecurityController.isHttpsOnly(activity))
                3 -> BrowserSecurityController.setBlockThirdPartyCookies(activity, !BrowserSecurityController.blockThirdPartyCookies(activity))
                4 -> BrowserDataStore(activity).clearHistory()
                5 -> { CookieManager.getInstance().removeAllCookies { }; CookieManager.getInstance().flush() }
                6 -> { BrowserDataStore(activity).clearHistory(); BrowserSecurityController.clearBrowsingData((activity.application as UniversalBrowserApp).getRuntime()) }
            }
            Toast.makeText(activity, "Privacy setting updated", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Close", null).show()
    }

    private fun showSitePermissions(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("Site permissions")
            .setMessage("Universal Browser asks before sensitive site access such as location, notifications and camera/microphone. Decisions are remembered per site for normal tabs. Private tabs do not persist permission decisions.")
            .setPositiveButton("Clear remembered decisions") { _, _ -> BrowserPermissionController.clearSiteDecisions(activity); Toast.makeText(activity, "Site permission decisions cleared", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("Done", null).show()
    }
}