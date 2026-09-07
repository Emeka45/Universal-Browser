package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

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
    private val purple = Color.rgb(101, 72, 255)
    private val violet = Color.rgb(145, 74, 255)
    private val darkPurple = Color.rgb(50, 32, 132)
    private val ink = Color.rgb(27, 26, 39)
    private val muted = Color.rgb(105, 103, 123)
    private val surface = Color.rgb(247, 246, 251)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val runtime = (application as UniversalBrowserApp).getRuntime()
        BrowserSecurityController.applyRuntimePolicy(this, runtime)
        tabManager = BrowserTabManager(runtime, TabStateStore(this))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        root.addView(buildToolbar())
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(purple); visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, 3.dp()))
        browserView = GeckoView(this)
        homePanel = buildHomePanel()
        root.addView(homePanel, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(browserView, LinearLayout.LayoutParams(-1, 0, 1f))
        browserView.visibility = View.GONE
        setContentView(root)
        restoreTabs()
    }

    @Suppress("DEPRECATION") override fun onBackPressed() {
        when { canGoBack -> activeSession()?.goBack(); homePanel.visibility == View.VISIBLE -> super.onBackPressed(); else -> showHome() }
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
        row.addView(TextView(this).apply { text = "U"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = gradient(intArrayOf(violet, purple, darkPurple), 13.dp()) }, LinearLayout.LayoutParams(39.dp(), 39.dp()).apply { setMargins(3.dp(), 0, 7.dp(), 0) })
        addressBar = EditText(this).apply { hint = "Search or enter address"; textSize = 14.5f; isSingleLine = true; setTextColor(ink); setPadding(16.dp(), 0, 14.dp(), 0); background = rounded(surface, 22.dp()); setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true } }
        row.addView(addressBar, LinearLayout.LayoutParams(0, 44.dp(), 1f))
        tabButton = toolbarButton("1", 14f) { showTabs() }
        row.addView(tabButton)
        row.addView(toolbarButton("↻", 21f) { activeSession()?.reload() ?: showHome() })
        row.addView(toolbarButton("⋮", 23f) { showBrowserMenu() })
        outer.addView(row); return outer
    }

    private fun buildHomePanel(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(surface) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18.dp(), 18.dp(), 18.dp(), 34.dp()) }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(22.dp(), 28.dp(), 22.dp(), 28.dp()); background = gradient(intArrayOf(darkPurple, purple, violet), 28.dp()) }
        hero.addView(TextView(this).apply { text = "U"; textSize = 58f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = gradient(intArrayOf(violet, purple), 31.dp()) }, LinearLayout.LayoutParams(104.dp(), 104.dp()).apply { bottomMargin = 17.dp() })
        hero.addView(TextView(this).apply { text = "UNIVERSAL"; textSize = 29f; letterSpacing = .08f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        hero.addView(TextView(this).apply { text = "Fast, private, dependable browsing."; textSize = 15f; setTextColor(Color.rgb(235, 231, 255)); gravity = Gravity.CENTER; setPadding(0, 4.dp(), 0, 20.dp()) })
        hero.addView(EditText(this).apply { hint = "Search the web or enter a URL"; textSize = 15f; isSingleLine = true; setTextColor(ink); setPadding(18.dp(), 0, 18.dp(), 0); background = rounded(Color.WHITE, 19.dp()); setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true } }, LinearLayout.LayoutParams(-1, 54.dp()))
        content.addView(hero)
        content.addView(sectionTitle("Browser", "Only the features that belong in a quality browser"), featureParams().apply { topMargin = 24.dp(); bottomMargin = 10.dp() })
        content.addView(featureCard("New tab", "Open another browser session", "+") { newTab(false) })
        content.addView(featureCard("Private tab", "Isolated private browsing", "◈") { newTab(true) }, featureParams())
        content.addView(featureCard("Tabs", "Switch, close and restore tabs", "▣") { showTabs() }, featureParams())
        content.addView(featureCard("Downloads", "Manage browser downloads", "↓") { DownloadCenter.show(this) }, featureParams())
        content.addView(featureCard("Browser tools", "Desktop site, zoom, reader, translation, PDF and privacy", "⚙") { BrowserPowerCenter.show(this, { activeSession() }, { currentUrl }, { activeSession()?.reload() }) }, featureParams())
        scroll.addView(content); return scroll
    }

    private fun attachTab(tab: BrowserTabManager.Tab) {
        if (!tab.session.isOpen) tab.session.open((application as UniversalBrowserApp).getRuntime())
        browserView.setSession(tab.session)
        BrowserPowerCenter.applyPreferences(this, tab.session)
        BrowserPermissionController.attach(this, tab.session)
        currentUrl = tab.url; addressBar.setText(tab.url); canGoBack = false; canGoForward = false
        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(session: GeckoSession) { recoverTab() }
            override fun onKill(session: GeckoSession) { recoverTab() }
        }
        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, value: Int) { if (session === activeSession()) { progress.progress = value; progress.visibility = if (value in 1..99) View.VISIBLE else View.GONE } }
            override fun onSessionStateChange(session: GeckoSession, state: GeckoSession.SessionState) { tabManager.updateSessionState(session, state) }
        }
        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (session !== activeSession()) return
                currentUrl = url ?: currentUrl; tabManager.updateUrl(session, currentUrl)
                if (currentUrl.isNotBlank()) browserData.recordVisit(currentUrl, tab.label.ifBlank { currentUrl })
                addressBar.setText(currentUrl); updateChromeState()
            }
            override fun onCanGoBack(session: GeckoSession, value: Boolean) { if (session === activeSession()) { canGoBack = value; updateChromeState() } }
            override fun onCanGoForward(session: GeckoSession, value: Boolean) { if (session === activeSession()) { canGoForward = value; updateChromeState() } }
        }
        updateChromeState()
    }

    private fun navigate(raw: String) {
        val input = raw.trim(); if (input.isEmpty()) return
        val tab = tabManager.active() ?: tabManager.createAndOpen(false).also { attachTab(it) }
        val uri = when { input.startsWith("http://", true) || input.startsWith("https://", true) -> input; input.contains(".") && !input.contains(" ") -> "https://$input"; else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(input, "UTF-8")}" }
        if (!BrowserSecurityController.shouldAllowNavigation(this, uri)) { toast("This address was blocked by browser security."); return }
        currentUrl = uri; tabManager.updateUrl(tab.session, uri); tab.session.setActive(true); tab.session.setFocused(true); tab.session.loadUri(uri); addressBar.setText(uri); showPage()
    }

    private fun newTab(privateMode: Boolean) { val tab = tabManager.createAndOpen(privateMode); attachTab(tab); showHome(); updateTabButton() }
    private fun switchTab(index: Int) { val tab = tabManager.activate(index) ?: return; attachTab(tab); if (tab.url.isBlank()) showHome() else showPage(); updateTabButton() }
    private fun closeTab(index: Int) { val next = tabManager.close(index); if (next == null) { val created = tabManager.createAndOpen(false); attachTab(created); showHome() } else { attachTab(next); if (next.url.isBlank()) showHome() else showPage() }; updateTabButton() }

    private fun showTabs() {
        val dialog = AlertDialog.Builder(this).setTitle("Tabs (${tabManager.count()})").setNegativeButton("Close", null).create()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8.dp(), 4.dp(), 8.dp(), 8.dp()) }
        tabManager.all().forEachIndexed { index, tab ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(12.dp(), 10.dp(), 8.dp(), 10.dp()); background = rounded(if (index == tabManager.activeIndex()) Color.rgb(238, 234, 255) else Color.WHITE, 16.dp()) }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            info.addView(TextView(this).apply { text = "${if (tab.privateMode) "🔒 " else ""}${tab.label.ifBlank { "New tab" }}"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
            info.addView(TextView(this).apply { text = tab.url.ifBlank { "New tab" }; textSize = 11f; setTextColor(muted); maxLines = 1 })
            row.addView(info)
            row.addView(TextView(this).apply { text = "×"; textSize = 23f; gravity = Gravity.CENTER; setTextColor(muted); setPadding(8.dp(), 0, 8.dp(), 0); setOnClickListener { closeTab(index); dialog.dismiss() } })
            row.setOnClickListener { switchTab(index); dialog.dismiss() }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp() })
        }
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER }
        footer.addView(TextView(this).apply { text = "+ New"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(purple); setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp()); setOnClickListener { dialog.dismiss(); newTab(false) } })
        footer.addView(TextView(this).apply { text = "Private"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(darkPurple); setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp()); setOnClickListener { dialog.dismiss(); newTab(true) } })
        list.addView(footer); val scroll = ScrollView(this); scroll.addView(list); dialog.setView(scroll); dialog.show()
    }

    private fun showBrowserMenu() {
        val items = arrayOf("New tab", "Private tab", "Tabs", "Downloads", "Browser tools", "Save page as PDF", "Screenshot page", "Site controls", "Close tab")
        AlertDialog.Builder(this).setTitle("Universal Browser").setItems(items) { _, which -> when (which) {
            0 -> newTab(false); 1 -> newTab(true); 2 -> showTabs(); 3 -> DownloadCenter.show(this)
            4 -> BrowserPowerCenter.show(this, { activeSession() }, { currentUrl }, { activeSession()?.reload() })
            5 -> activeSession()?.let { BrowserFeatureCenter.savePageAsPdf(this, it) }
            6 -> activeSession()?.let { BrowserFeatureCenter.captureVisiblePage(this, it) }
            7 -> activeSession()?.let { BrowserFeatureCenter.showSiteControls(this, it, currentUrl) }
            8 -> tabManager.activeIndex().takeIf { it >= 0 }?.let { closeTab(it) }
        } }.show()
    }

    private fun recoverTab() {
        runOnUiThread {
            toast("The page process stopped. Recovering the tab…")
            val tab = tabManager.active() ?: return@runOnUiThread
            val url = tab.url
            tab.session.close(); tab.session.open((application as UniversalBrowserApp).getRuntime()); attachTab(tab)
            if (url.isNotBlank()) tab.session.loadUri(url) else showHome()
        }
    }

    private fun activeSession(): GeckoSession? = tabManager.active()?.session
    private fun showHome() { homePanel.visibility = View.VISIBLE; browserView.visibility = View.GONE; progress.visibility = View.GONE }
    private fun showPage() { homePanel.visibility = View.GONE; browserView.visibility = View.VISIBLE }
    private fun updateChromeState() { backButton.alpha = if (canGoBack) 1f else .35f; forwardButton.alpha = if (canGoForward) 1f else .35f; updateTabButton() }
    private fun updateTabButton() { tabButton.text = tabManager.count().toString() }
    private fun toolbarButton(label: String, size: Float, action: () -> Unit) = TextView(this).apply { text = label; textSize = size; gravity = Gravity.CENTER; setTextColor(ink); setOnClickListener { action() }; setPadding(7.dp(), 0, 7.dp(), 0); layoutParams = LinearLayout.LayoutParams(38.dp(), 44.dp()) }
    private fun sectionTitle(title: String, subtitle: String) = TextView(this).apply { text = "$title\n$subtitle"; textSize = 16f; setTextColor(ink); setTypeface(Typeface.DEFAULT, Typeface.BOLD) }
    private fun featureParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp() }
    private fun featureCard(title: String, subtitle: String, icon: String, action: (() -> Unit)? = null) = TextView(this).apply { text = "$icon   $title\n       $subtitle"; textSize = 14f; setTextColor(ink); setPadding(16.dp(), 15.dp(), 16.dp(), 15.dp()); background = rounded(Color.WHITE, 18.dp()); setOnClickListener { action?.invoke() } }
    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun gradient(colors: IntArray, radius: Int) = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius.toFloat() }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
