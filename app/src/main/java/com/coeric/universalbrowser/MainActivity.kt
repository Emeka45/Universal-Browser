package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** Main browser surface; BrowserTabManager owns all Gecko sessions. */
class MainActivity : Activity() {
    private lateinit var browserView: GeckoView
    private lateinit var homePanel: View
    private lateinit var addressBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView
    private lateinit var tabButton: TextView
    private lateinit var tabManager: BrowserTabManager

    private val browserData by lazy { BrowserDataStore(this) }
    private var currentUrl = ""
    private var canGoBack = false
    private var canGoForward = false
    private var lastMediaUrl = ""
    private var lastMediaPromptAt = 0L

    private val purple = Color.rgb(101, 72, 255)
    private val violet = Color.rgb(145, 74, 255)
    private val darkPurple = Color.rgb(50, 32, 132)
    private val ink = Color.rgb(27, 26, 39)
    private val muted = Color.rgb(105, 103, 123)
    private val surface = Color.rgb(247, 246, 251)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = white
        window.navigationBarColor = white
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        tabManager = BrowserTabManager(getRuntime(), TabStateStore(this))

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(white) }
        root.addView(buildToolbar())
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progressTintList = android.content.res.ColorStateList.valueOf(purple); visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(-1, 3.dp()))
        browserView = GeckoView(this)
        homePanel = buildHomePanel()
        root.addView(homePanel, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(browserView, LinearLayout.LayoutParams(-1, 0, 1f))
        browserView.visibility = View.GONE
        setContentView(root)
        restoreTabs()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, systemBackCallback)
    }

    private val systemBackCallback = android.window.OnBackInvokedCallback { when { canGoBack -> activeSession()?.goBack(); homePanel.visibility == View.VISIBLE -> finish(); else -> showHome() } }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { when { canGoBack -> activeSession()?.goBack(); homePanel.visibility == View.VISIBLE -> super.onBackPressed(); else -> showHome() } }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        BrowserPermissionController.onAndroidPermissionResult(requestCode, grantResults)
    }

    private fun restoreTabs() {
        val restored = tabManager.restorePersistedTabs()
        val tab = tabManager.active() ?: restored.firstOrNull() ?: tabManager.createAndOpen(false)
        attachTab(tab)
        if (restored.isEmpty() || tab.url.isBlank()) showHome() else showPage()
        updateTabButton()
    }

    private fun buildToolbar(): View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8.dp(), 8.dp(), 8.dp(), 7.dp()) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        backButton = toolbarButton("‹", 28f) { if (canGoBack) activeSession()?.goBack() }
        forwardButton = toolbarButton("›", 28f) { if (canGoForward) activeSession()?.goForward() }
        row.addView(backButton); row.addView(forwardButton)
        row.addView(TextView(this).apply { text = "U"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(white); background = gradient(intArrayOf(violet, purple, darkPurple), 13.dp()) }, LinearLayout.LayoutParams(39.dp(), 39.dp()).apply { setMargins(3.dp(), 0, 7.dp(), 0) })
        addressBar = EditText(this).apply { hint = "Search or enter address"; textSize = 14.5f; isSingleLine = true; setTextColor(ink); setPadding(16.dp(), 0, 14.dp(), 0); background = rounded(surface, 22.dp()); setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true } }
        row.addView(addressBar, LinearLayout.LayoutParams(0, 44.dp(), 1f))
        tabButton = toolbarButton("1", 14f) { showTabs() }; row.addView(tabButton)
        row.addView(toolbarButton("↻", 21f) { activeSession()?.reload() ?: showHome() })
        row.addView(toolbarButton("⚡", 21f) { BrowserPowerCenter.show(this, { activeSession() }, { currentUrl }, { activeSession()?.reload() }) })
        row.addView(toolbarButton("⋮", 23f) { showBrowserMenu() })
        outer.addView(row); return outer
    }

    private fun buildHomePanel(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(surface) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18.dp(), 18.dp(), 18.dp(), 34.dp()) }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(22.dp(), 28.dp(), 22.dp(), 28.dp()); background = gradient(intArrayOf(darkPurple, purple, violet), 28.dp()) }
        hero.addView(TextView(this).apply { text = "U"; textSize = 58f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(white); background = gradient(intArrayOf(violet, purple), 31.dp()) }, LinearLayout.LayoutParams(104.dp(), 104.dp()).apply { bottomMargin = 17.dp() })
        hero.addView(TextView(this).apply { text = "UNIVERSAL"; textSize = 29f; letterSpacing = .08f; typeface = Typeface.DEFAULT_BOLD; setTextColor(white); gravity = Gravity.CENTER })
        hero.addView(TextView(this).apply { text = "Your web. Your way."; textSize = 15f; setTextColor(Color.rgb(235, 231, 255)); gravity = Gravity.CENTER; setPadding(0, 4.dp(), 0, 20.dp()) })
        hero.addView(EditText(this).apply { hint = "Search the web or enter a URL"; textSize = 15f; isSingleLine = true; setTextColor(ink); setPadding(18.dp(), 0, 18.dp(), 0); background = rounded(white, 19.dp()); setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true } }, LinearLayout.LayoutParams(-1, 54.dp()))
        content.addView(hero)
        content.addView(sectionTitle("Your browser", "Real Gecko sessions, private tabs and persistent tabs"), featureParams().apply { topMargin = 24.dp(); bottomMargin = 10.dp() })
        content.addView(featureCard("New tab", "Open another independent browser session", "+") { newTab(false) })
        content.addView(featureCard("Private tab", "Isolated private browsing; never persisted", "◈") { newTab(true) }, featureParams())
        content.addView(featureCard("Tabs", "Switch, close and restore tabs", "▣") { showTabs() }, featureParams())
        content.addView(featureCard("Downloads", "Progress, pause state, retry, cancel and files", "↓") { DownloadCenter.show(this) }, featureParams())
        content.addView(featureCard("Web Stores", "Firefox, Chrome, Edge and Opera catalogs", "◎") { showWebStores() }, featureParams())
        content.addView(featureCard("Power tools", "Desktop site, zoom, reader, translation, PDF, screenshot and privacy", "⚙") { BrowserPowerCenter.show(this, { activeSession() }, { currentUrl }, { activeSession()?.reload() }) }, featureParams())
        content.addView(featureCard("Universal AI", "Ask about the current page through the secure gateway", "AI") { showAiAssistant() }, featureParams())
        scroll.addView(content); return scroll
    }

    private fun attachTab(tab: BrowserTabManager.Tab) {
        val old = browserView.getSession()
        if (old != null && old !== tab.session) browserView.releaseSession()
        if (!tab.session.isOpen) tab.session.open(getRuntime())
        browserView.setSession(tab.session)
        BrowserPowerCenter.applyPreferences(this, tab.session)
        BrowserPermissionController.attach(this, tab.session)
        currentUrl = tab.url; addressBar.setText(tab.url); canGoBack = false; canGoForward = false
        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(session: GeckoSession) { recoverActiveTab("The page process stopped and was restarted.") }
            override fun onKill(session: GeckoSession) { recoverActiveTab("Android stopped the page process; the tab was recovered.") }
        }
        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, value: Int) { if (session === activeSession()) { progress.progress = value; progress.visibility = if (value in 1..99) View.VISIBLE else View.GONE } }
            override fun onSessionStateChange(session: GeckoSession, state: GeckoSession.SessionState) { tabManager.updateSessionState(session, state); updateChromeState() }
        }
        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (session !== activeSession()) return
                currentUrl = url ?: currentUrl; tab.url = currentUrl
                if (currentUrl.isNotBlank()) browserData.recordVisit(currentUrl, tab.label.ifBlank { currentUrl })
                addressBar.setText(currentUrl); updateChromeState()
            }
            override fun onCanGoBack(session: GeckoSession, value: Boolean) { if (session === activeSession()) { canGoBack = value; updateChromeState() } }
            override fun onCanGoForward(session: GeckoSession, value: Boolean) { if (session === activeSession()) { canGoForward = value; updateChromeState() } }
        }
        getRuntime().webExtensionController.promptDelegate = extensionPromptDelegate
        installMediaDetector(tab.session)
        updateChromeState()
    }

    private fun installMediaDetector(session: GeckoSession) { getRuntime().webExtensionController.ensureBuiltIn("resource://android/assets/media-detector/", MEDIA_DETECTOR_ID).accept({ extension -> extension?.let { session.getWebExtensionController().setMessageDelegate(it, mediaMessageDelegate, NATIVE_APP_NAME) } }, { error -> android.util.Log.e("UniversalBrowser", "Media detector unavailable", error) }) }

    private val mediaMessageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            if (nativeApp != NATIVE_APP_NAME || sender.session !== activeSession() || message !is JSONObject) return null
            if (message.optString("type") != "media-playable") return null
            val url = message.optString("url").trim(); if (!AdvancedMediaDownloadEngine.isSupported(url)) return null
            runOnUiThread { offerMediaDownload(url, message.optString("title").trim().ifBlank { "Video" }) }; return null
        }
    }

    private fun offerMediaDownload(url: String, title: String) {
        val now = System.currentTimeMillis(); if (url == lastMediaUrl && now - lastMediaPromptAt < 8_000L) return
        lastMediaUrl = url; lastMediaPromptAt = now
        AlertDialog.Builder(this).setTitle("Media ready to download").setMessage(title.take(100)).setNegativeButton("Not now", null).setPositiveButton("Download") { _, _ -> AdvancedMediaDownloadEngine.enqueue(this, url, title, currentUrl, { toast("Media download started") }, { result -> toast("${result.kind} saved: ${result.fileName}") }, { error -> toast("Media download failed: $error") }) }.show()
    }

    private fun navigate(raw: String) {
        val input = raw.trim(); if (input.isEmpty()) return
        val tab = tabManager.active() ?: tabManager.createAndOpen(false).also { attachTab(it) }
        val uri = when { input.startsWith("http://", true) || input.startsWith("https://", true) -> input; input.contains(".") && !input.contains(" ") -> "https://$input"; else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(input, "UTF-8")}" }
        currentUrl = uri; tab.url = uri; tab.session.setActive(true); tab.session.setFocused(true); tab.session.loadUri(uri); addressBar.setText(uri); showPage()
    }

    private fun newTab(privateMode: Boolean) { val tab = tabManager.createAndOpen(privateMode); attachTab(tab); showHome(); updateTabButton(); toast(if (privateMode) "Private tab opened" else "New tab opened") }
    private fun switchTab(index: Int) { val tab = tabManager.activate(index) ?: return; attachTab(tab); if (tab.url.isBlank()) showHome() else showPage(); updateTabButton() }
    private fun closeTab(index: Int) { val next = tabManager.close(index); canGoBack = false; canGoForward = false; if (next == null) { val created = tabManager.createAndOpen(false); attachTab(created); showHome() } else { attachTab(next); if (next.url.isBlank()) showHome() else showPage() }; updateTabButton() }

    private fun showTabs() {
        val dialog = AlertDialog.Builder(this).setTitle("Tabs (${tabManager.count()})").setNegativeButton("Close", null).create()
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8.dp(), 4.dp(), 8.dp(), 8.dp()) }
        tabManager.all().forEachIndexed { index, tab ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(12.dp(), 10.dp(), 8.dp(), 10.dp()); background = rounded(if (index == tabManager.activeIndex()) Color.rgb(238, 234, 255) else white, 16.dp()) }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            info.addView(TextView(this).apply { text = "${if (tab.privateMode) "🔒 " else ""}${tab.label.ifBlank { "New tab" }}${if (index == tabManager.activeIndex()) "  • Active" else ""}"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
            info.addView(TextView(this).apply { text = tab.url.ifBlank { "New tab" }; textSize = 11f; setTextColor(muted); maxLines = 1 })
            row.addView(info)
            row.addView(TextView(this).apply { text = "×"; textSize = 23f; gravity = Gravity.CENTER; setTextColor(muted); setPadding(8.dp(), 0, 8.dp(), 0); setOnClickListener { closeTab(index); dialog.dismiss() } })
            row.setOnClickListener { switchTab(index); dialog.dismiss() }
            row.setOnLongClickListener { showTabActions(index, dialog); true }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp() })
        }
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, 4.dp(), 0, 4.dp()) }
        footer.addView(TextView(this).apply { text = "+ New"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(purple); setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp()); setOnClickListener { dialog.dismiss(); newTab(false) } })
        footer.addView(TextView(this).apply { text = "Private"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(darkPurple); setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp()); setOnClickListener { dialog.dismiss(); newTab(true) } })
        list.addView(footer)
        scroll.addView(list); dialog.setView(scroll); dialog.show()
    }

    private fun showTabActions(index: Int, parent: AlertDialog) {
        val actions = arrayOf("Switch", "Duplicate", "Close", "Close other tabs", "Close tabs to the right")
        AlertDialog.Builder(this).setTitle("Tab actions").setItems(actions) { _, which -> when (which) {
            0 -> { parent.dismiss(); switchTab(index) }
            1 -> { parent.dismiss(); tabManager.duplicate(index)?.let { attachTab(it); showPage() } }
            2 -> { parent.dismiss(); closeTab(index) }
            3 -> { parent.dismiss(); tabManager.closeOthers(index); val tab = tabManager.active(); if (tab != null) { attachTab(tab); if (tab.url.isBlank()) showHome() else showPage() }; updateTabButton() }
            4 -> { parent.dismiss(); tabManager.closeToRight(index); val tab = tabManager.active(); if (tab != null) { attachTab(tab); if (tab.url.isBlank()) showHome() else showPage() }; updateTabButton() }
        } }.setNegativeButton("Cancel", null).show()
    }

    private fun showHome() { homePanel.visibility = View.VISIBLE; browserView.visibility = View.GONE; addressBar.setText(""); updateTabButton() }
    private fun showPage() { homePanel.visibility = View.GONE; browserView.visibility = View.VISIBLE; updateChromeState() }
    private fun activeSession(): GeckoSession? = tabManager.active()?.session
    private fun updateChromeState() { backButton.alpha = if (canGoBack) 1f else .35f; forwardButton.alpha = if (canGoForward) 1f else .35f; updateTabButton() }
    private fun updateTabButton() { if (::tabButton.isInitialized) tabButton.text = tabManager.count().toString() }
    private fun recoverActiveTab(message: String) { runOnUiThread { val tab = tabManager.active() ?: return@runOnUiThread; try { if (!tab.session.isOpen) tab.session.open(getRuntime()); if (tab.url.isNotBlank()) tab.session.loadUri(tab.url); toast(message) } catch (_: Throwable) { showHome() } } }

    private fun showBrowserMenu() {
        val items = arrayOf("Home", "New tab", "New private tab", "Tabs", "Downloads", "Extensions", "Web Stores", "Power tools", "Universal AI", "About Universal")
        AlertDialog.Builder(this).setTitle("Universal").setItems(items) { _, which -> when (which) { 0 -> showHome(); 1 -> newTab(false); 2 -> newTab(true); 3 -> showTabs(); 4 -> DownloadCenter.show(this); 5 -> showExtensions(); 6 -> showWebStores(); 7 -> BrowserPowerCenter.show(this, { activeSession() }, { currentUrl }, { activeSession()?.reload() }); 8 -> showAiAssistant(); 9 -> showAbout() } }.show()
    }

    private fun showExtensions() { BrowserExtensionCenter.showManager(this, getRuntime()) }

    private fun showWebStores() {
        val stores = arrayOf("Firefox Add-ons", "Chrome Web Store", "Microsoft Edge Add-ons", "Opera Add-ons")
        val urls = arrayOf("https://addons.mozilla.org/android/", "https://chromewebstore.google.com/", "https://microsoftedge.microsoft.com/addons/", "https://addons.opera.com/")
        AlertDialog.Builder(this).setTitle("Extension web stores").setItems(stores) { _, which -> navigate(urls[which]) }.setNegativeButton("Close", null).show()
    }

    private fun showAiAssistant() { if (currentUrl.isBlank()) toast("Open a webpage first") else AiAssistantView.show(this, currentUrl) }
    private fun showAbout() { AlertDialog.Builder(this).setTitle("Universal Browser").setMessage("GeckoView browser with real multi-session tabs, private browsing isolation, persistent normal sessions, WebExtensions, media downloads and native AI foundation.\n\nPrivate tabs are never persisted.").setPositiveButton("Done", null).show() }

    private val extensionPromptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollectionPermissions: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse> {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>(); val name = extension.metaData.name?.takeIf { it.isNotBlank() } ?: extension.id
            val requested = (permissions.toList() + origins.toList() + dataCollectionPermissions.toList()).distinct().joinToString("\n").ifBlank { "No additional permissions listed." }
            runOnUiThread { AlertDialog.Builder(this@MainActivity).setTitle("Install $name?").setMessage("This extension requests:\n\n$requested").setNegativeButton("Cancel") { _, _ -> result.complete(WebExtension.PermissionPromptResponse(false, false, false)) }.setPositiveButton("Install") { _, _ -> result.complete(WebExtension.PermissionPromptResponse(true, false, false)) }.setOnCancelListener { result.complete(WebExtension.PermissionPromptResponse(false, false, false)) }.show() }
            return result
        }
    }

    override fun onPause() { tabManager.persist(); super.onPause() }
    override fun onDestroy() { BrowserPermissionController.clearPendingAndroidRequest(); tabManager.persist(); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) onBackInvokedDispatcher.unregisterOnBackInvokedCallback(systemBackCallback); super.onDestroy() }

    private fun toolbarButton(label: String, size: Float, action: () -> Unit) = TextView(this).apply { text = label; textSize = size; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink); setOnClickListener { action() }; background = rounded(Color.TRANSPARENT, 12.dp()); layoutParams = LinearLayout.LayoutParams(40.dp(), 42.dp()) }
    private fun sectionTitle(title: String, subtitle: String): View = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(TextView(this@MainActivity).apply { text = title; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) }); addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 12f; setTextColor(muted) }) }
    private fun featureCard(title: String, subtitle: String, mark: String, action: () -> Unit): View = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(14.dp(), 13.dp(), 12.dp(), 13.dp()); background = rounded(white, 19.dp()); elevation = 2.dp().toFloat(); setOnClickListener { action() }; addView(TextView(this@MainActivity).apply { text = mark; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(white); background = gradient(intArrayOf(purple, violet), 15.dp()) }, LinearLayout.LayoutParams(48.dp(), 48.dp()).apply { rightMargin = 13.dp() }); addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); addView(TextView(this@MainActivity).apply { text = title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) }); addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 11.5f; setTextColor(muted) }) }); addView(TextView(this@MainActivity).apply { text = "›"; textSize = 25f; setTextColor(muted) }) }
    private fun featureParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp() }
    private fun getRuntime(): GeckoRuntime = (application as UniversalBrowserApp).getRuntime()
    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun gradient(colors: IntArray, radius: Int): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius.toFloat() }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object { private const val MEDIA_DETECTOR_ID = "media-detector@universalbrowser.coeric"; private const val NATIVE_APP_NAME = "browser" }
}
