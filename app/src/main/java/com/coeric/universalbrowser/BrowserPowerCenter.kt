package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/** Central browser power tools: desktop mode, zoom, sharing, page actions and privacy controls. */
object BrowserPowerCenter {
    private const val PREFS = "universal_browser_power"
    private const val DESKTOP = "desktop_mode"
    private const val TEXT_SCALE = "text_scale"

    fun show(activity: Activity, sessionProvider: () -> GeckoSession?, currentUrlProvider: () -> String, reload: () -> Unit) {
        val session = sessionProvider()
        if (session == null) {
            Toast.makeText(activity, "Open a page first", Toast.LENGTH_SHORT).show()
            return
        }
        val items = arrayOf(
            "Desktop site", "Page zoom", "Find in page", "Share page", "Save page as PDF",
            "Screenshot page", "Print page", "Bookmark page", "Site controls",
            "Privacy & protection", "Web stores", "Downloads"
        )
        AlertDialog.Builder(activity).setTitle("Universal tools").setItems(items) { _, which ->
            when (which) {
                0 -> toggleDesktop(activity, session, reload)
                1 -> showZoom(activity, session, reload)
                2 -> findInPage(activity, session)
                3 -> share(activity, currentUrlProvider())
                4 -> BrowserFeatureCenter.savePageAsPdf(activity, session)
                5 -> BrowserFeatureCenter.captureVisiblePage(activity, session)
                6 -> BrowserFeatureCenter.printPage(activity, session)
                7 -> bookmark(activity, currentUrlProvider())
                8 -> BrowserFeatureCenter.showSiteControls(activity, session, currentUrlProvider())
                9 -> showPrivacy(activity, session)
                10 -> showStores(activity)
                11 -> showDownloads(activity)
            }
        }.setNegativeButton("Close", null).show()
    }

    fun applyPreferences(activity: Activity, session: GeckoSession) {
        val prefs = activity.getSharedPreferences(PREFS, 0)
        val desktop = prefs.getBoolean(DESKTOP, false)
        session.settings.setUserAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        session.settings.setViewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
    }

    private fun toggleDesktop(activity: Activity, session: GeckoSession, reload: () -> Unit) {
        val prefs = activity.getSharedPreferences(PREFS, 0)
        val enabled = !prefs.getBoolean(DESKTOP, false)
        prefs.edit().putBoolean(DESKTOP, enabled).apply()
        session.settings.setUserAgentMode(if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        session.settings.setViewportMode(if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        reload()
        Toast.makeText(activity, if (enabled) "Desktop site enabled" else "Mobile site enabled", Toast.LENGTH_SHORT).show()
    }

    private fun showZoom(activity: Activity, session: GeckoSession, reload: () -> Unit) {
        val values = arrayOf("50%", "60%", "70%", "80%", "90%", "100%", "110%", "125%", "150%", "175%", "200%", "225%", "250%")
        val prefs = activity.getSharedPreferences(PREFS, 0)
        val current = prefs.getFloat(TEXT_SCALE, 1f)
        val selected = values.indexOfFirst { kotlin.math.abs(it.dropLast(1).toFloat() / 100f - current) < 0.01f }.let { if (it < 0) 5 else it }
        AlertDialog.Builder(activity).setTitle("Page zoom").setSingleChoiceItems(values, selected) { dialog, which ->
            val factor = values[which].dropLast(1).toFloat() / 100f
            prefs.edit().putFloat(TEXT_SCALE, factor).apply()
            Toast.makeText(activity, "Page zoom ${values[which]}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            reload()
        }.setNeutralButton("Reset 100%") { _, _ ->
            prefs.edit().putFloat(TEXT_SCALE, 1f).apply()
            Toast.makeText(activity, "Page zoom reset to 100%", Toast.LENGTH_SHORT).show()
            reload()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun findInPage(activity: Activity, session: GeckoSession) {
        val input = EditText(activity).apply { hint = "Find text on this page" }
        AlertDialog.Builder(activity).setTitle("Find in page").setView(input)
            .setNegativeButton("Close", null)
            .setPositiveButton("Find") { _, _ ->
                val term = input.text.toString().trim()
                if (term.isBlank()) return@setPositiveButton
                val escaped = term.replace("\\", "\\\\").replace("'", "\\'")
                session.loadUri("javascript:(function(){var q='$escaped';var s=window.getSelection();s.removeAllRanges();var r=document.createRange();var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);var n;while(n=w.nextNode()){var i=n.nodeValue.toLowerCase().indexOf(q.toLowerCase());if(i>=0){r.setStart(n,i);r.setEnd(n,i+q.length);s.addRange(r);n.parentElement.scrollIntoView({block:'center'});break;}}})()")
            }.show()
    }

    private fun share(activity: Activity, url: String) {
        if (url.isBlank()) return
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }, "Share page"))
    }

    private fun bookmark(activity: Activity, url: String) {
        if (url.isBlank()) return
        BrowserDataStore(activity).addBookmark(url, try { Uri.parse(url).host ?: url } catch (_: Throwable) { url })
        Toast.makeText(activity, "Bookmarked", Toast.LENGTH_SHORT).show()
    }

    private fun showPrivacy(activity: Activity, session: GeckoSession) {
        val settings = session.settings
        val items = arrayOf(
            "Tracking protection: ${if (settings.useTrackingProtection) "On" else "Off"}",
            "JavaScript: ${if (settings.allowJavascript) "On" else "Off"}",
            "Clear history",
            "Clear cookies",
            "Clear site data"
        )
        AlertDialog.Builder(activity).setTitle("Privacy & protection").setItems(items) { _, which ->
            when (which) {
                0 -> { settings.useTrackingProtection = !settings.useTrackingProtection; Toast.makeText(activity, "Tracking protection updated", Toast.LENGTH_SHORT).show() }
                1 -> { settings.allowJavascript = !settings.allowJavascript; Toast.makeText(activity, "JavaScript setting updated", Toast.LENGTH_SHORT).show() }
                2 -> { BrowserDataStore(activity).clearHistory(); Toast.makeText(activity, "History cleared", Toast.LENGTH_SHORT).show() }
                3 -> { CookieManager.getInstance().removeAllCookies { runOnUiThreadSafe(activity) { Toast.makeText(activity, "Cookies cleared", Toast.LENGTH_SHORT).show() } }; CookieManager.getInstance().flush() }
                4 -> {
                    BrowserDataStore(activity).clearHistory()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().removeSessionCookies(null)
                    CookieManager.getInstance().flush()
                    Toast.makeText(activity, "Local browser data cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }.setNegativeButton("Close", null).show()
    }

    private fun runOnUiThreadSafe(activity: Activity, action: () -> Unit) {
        activity.runOnUiThread(action)
    }

    private fun showStores(activity: Activity) {
        val stores = arrayOf("Firefox Add-ons", "Chrome Web Store", "Microsoft Edge Add-ons", "Opera Add-ons")
        val urls = arrayOf("https://addons.mozilla.org/android/", "https://chromewebstore.google.com/", "https://microsoftedge.microsoft.com/addons/", "https://addons.opera.com/")
        AlertDialog.Builder(activity).setTitle("Extension web stores").setItems(stores) { _, which -> activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urls[which]))) }
            .setNegativeButton("Close", null).show()
    }

    private fun showDownloads(activity: Activity) {
        try { activity.startActivity(Intent("android.intent.action.VIEW_DOWNLOADS")) }
        catch (_: Throwable) { Toast.makeText(activity, "Open the Android Downloads app to view downloads", Toast.LENGTH_SHORT).show() }
    }
}
