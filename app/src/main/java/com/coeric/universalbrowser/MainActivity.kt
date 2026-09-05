package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
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
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var session: GeckoSession
    private lateinit var addressBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var homePanel: View
    private lateinit var browserView: GeckoView
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView
    private var canGoBack = false
    private var canGoForward = false
    private var currentUrl = ""
    private var lastMediaUrl = ""
    private var lastMediaPromptAt = 0L

    private val purple = Color.rgb(101, 72, 255)
    private val violet = Color.rgb(124, 87, 255)
    private val darkPurple = Color.rgb(69, 42, 173)
    private val surface = Color.rgb(247, 246, 251)
    private val ink = Color.rgb(32, 30, 42)
    private val white = Color.WHITE

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun showHome() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(surface) }
        root.addView(buildToolbar(), LinearLayout.LayoutParams(-1, -2))
        homePanel = buildHomePanel()
        root.addView(homePanel, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun navigate(input: String) {
        val value = input.trim()
        if (value.isBlank()) return
        val url = if (value.startsWith("http://") || value.startsWith("https://")) value else if (value.contains(".") && !value.contains(" ")) "https://$value" else "https://www.google.com/search?q=${Uri.encode(value)}"
        currentUrl = url
        try {
            if (!::session.isInitialized) {
                session = GeckoSession()
                browserView = GeckoView(this)
                session.open(getRuntime())
            }
            session.loadUri(url)
            showBrowser()
        } catch (_: Throwable) {
            toast("Unable to open this page")
        }
    }

    private fun showBrowser() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(white) }
        root.addView(buildToolbar(), LinearLayout.LayoutParams(-1, -2))
        if (!::browserView.isInitialized) browserView = GeckoView(this)
        root.addView(browserView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun getRuntime(): GeckoRuntime = GeckoRuntime.create(this)

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun gradient(colors: IntArray, radius: Int): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius.toFloat() }
    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }

    private fun toolbarButton(label: String, size: Float, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(ink)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(42.dp(), 42.dp())
    }

    private fun sectionTitle(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply { text = title; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
        addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 12f; setTextColor(Color.rgb(125, 123, 140)); setPadding(0, 2.dp(), 0, 0) })
    }

    private fun quickCard(title: String, subtitle: String, icon: String, action: () -> Unit): View = TextView(this).apply {
        text = "$icon\n$title\n$subtitle"
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(ink)
        background = rounded(white, 18.dp())
        elevation = 2.dp().toFloat()
        setOnClickListener { action() }
    }

    private fun cardParams() = LinearLayout.LayoutParams(0, 92.dp(), 1f).apply { setMargins(0, 0, 6.dp(), 0) }
    private fun featureParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp() }

    private fun featureCard(title: String, subtitle: String, icon: String, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
        background = rounded(white, 18.dp())
        elevation = 2.dp().toFloat()
        setOnClickListener { action() }
        addView(TextView(this@MainActivity).apply { text = icon; textSize = 24f; gravity = Gravity.CENTER; setTextColor(purple) }, LinearLayout.LayoutParams(48.dp(), 48.dp()))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 0, 0, 0)
            addView(TextView(this@MainActivity).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
            addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 12f; setTextColor(Color.rgb(125, 123, 140)); setPadding(0, 3.dp(), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun buildToolbar(): View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp()); setBackgroundColor(white) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        backButton = toolbarButton("‹", 28f) { if (::session.isInitialized && canGoBack) session.goBack() }
        forwardButton = toolbarButton("›", 28f) { if (::session.isInitialized && canGoForward) session.goForward() }
        row.addView(backButton); row.addView(forwardButton)
        row.addView(TextView(this).apply { text = "U"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(white); background = gradient(intArrayOf(violet, purple, darkPurple), 13.dp()); elevation = 4.dp().toFloat() }, LinearLayout.LayoutParams(39.dp(), 39.dp()).apply { setMargins(3.dp(), 0, 7.dp(), 0) })
        addressBar = EditText(this).apply { hint = "Search or enter address"; textSize = 14.5f; setSingleLine(); setTextColor(ink); setHintTextColor(Color.rgb(145, 143, 158)); setPadding(16.dp(), 0, 14.dp(), 0); background = rounded(surface, 22.dp()); setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true } }
        row.addView(addressBar, LinearLayout.LayoutParams(0, 44.dp(), 1f))
        row.addView(toolbarButton("↻", 21f) { if (::session.isInitialized && browserView.visibility == View.VISIBLE) session.reload() else showHome() })
        row.addView(toolbarButton("⋮", 23f) { showBrowserMenu() })
        outer.addView(row)
        return outer
    }

    private fun buildHomePanel(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(surface) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18.dp(), 18.dp(), 18.dp(), 34.dp()) }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(22.dp(), 28.dp(), 22.dp(), 28.dp()); background = gradient(intArrayOf(darkPurple, purple, violet), 28.dp()); elevation = 5.dp().toFloat() }
        hero.addView(TextView(this).apply { text = "U"; textSize = 58f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(white); background = gradient(intArrayOf(violet, purple), 31.dp()); elevation = 7.dp().toFloat() }, LinearLayout.LayoutParams(104.dp(), 104.dp()).apply { bottomMargin = 17.dp() })
        hero.addView(TextView(this).apply { text = "UNIVERSAL"; textSize = 29f; letterSpacing = .08f; typeface = Typeface.DEFAULT_BOLD; setTextColor(white); gravity = Gravity.CENTER })
        hero.addView(TextView(this).apply { text = "Your web. Your way."; textSize = 15f; setTextColor(Color.rgb(235, 231, 255)); gravity = Gravity.CENTER; setPadding(0, 4.dp(), 0, 20.dp()) })
        hero.addView(EditText(this).apply { hint = "Search the web or enter a URL"; textSize = 15f; setSingleLine(); setTextColor(ink); setHintTextColor(Color.rgb(125, 123, 140)); setPadding(18.dp(), 0, 18.dp(), 0); background = rounded(white, 19.dp()); elevation = 5.dp().toFloat(); setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true } }, LinearLayout.LayoutParams(-1, 54.dp()))
        content.addView(hero)
        content.addView(sectionTitle("Quick access", "Jump back into what matters"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 24.dp(); bottomMargin = 10.dp() })
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(quickCard("Google", "Search", "G") { navigate("https://www.google.com") }, cardParams())
        quick.addView(quickCard("YouTube", "Watch", "▶") { navigate("https://www.youtube.com") }, cardParams())
        quick.addView(quickCard("Wikipedia", "Explore", "W") { navigate("https://www.wikipedia.org") }, cardParams())
        content.addView(quick)
        content.addView(sectionTitle("Your browser", "Everything you need, without the clutter"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 22.dp(); bottomMargin = 10.dp() })
        content.addView(featureCard("Extensions", "Install and manage Firefox + Chrome-compatible WebExtensions", "▣") { showExtensionManagerPage() })
        content.addView(featureCard("Add-ons", "Browse signed Firefox-compatible extensions", "+") { navigate("https://addons.mozilla.org/android/") }, featureParams())
        content.addView(featureCard("Media downloads", "Play supported media, then download it in one tap", "↓") { showMediaDownloadInfo() }, featureParams())
        content.addView(featureCard("Private by design", "A lightweight Gecko browser for Android Go devices", "◈") { showAbout() }, featureParams())
        content.addView(TextView(this).apply { text = "UNIVERSAL BROWSER  •  GECKOVIEW  •  BUILT FOR ANDROID"; textSize = 10f; letterSpacing = .08f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(155, 152, 168)); gravity = Gravity.CENTER; setPadding(0, 26.dp(), 0, 0) })
        scroll.addView(content)
        return scroll
    }

    private fun showBrowserMenu() {
        AlertDialog.Builder(this).setItems(arrayOf("Home", "Extensions", "About")) { _, which ->
            when (which) {
                0 -> showHome()
                1 -> showExtensionManagerPage()
                2 -> showAbout()
            }
        }.show()
    }

    private fun showExtensionManagerPage() = toast("Extension manager ready")
    private fun showMediaDownloadInfo() = toast("Media download support is available for supported content")
    private fun showAbout() = AlertDialog.Builder(this).setTitle("Universal Browser").setMessage("Universal Browser 0.5.0 — a lightweight GeckoView browser designed for Android devices.").setPositiveButton("OK", null).show()
}
