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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(white) }
        root.addView(buildToolbar())
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(purple)
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, 3.dp()))
        browserView = GeckoView(this)
        homePanel = buildHomePanel()
        root.addView(homePanel, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(browserView, LinearLayout.LayoutParams(-1, 0, 1f))
        browserView.visibility = View.GONE
        setContentView(root)
    }

    private fun ensureBrowserReady() {
        if (::session.isInitialized) return
        session = GeckoSession()
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(crashedSession: GeckoSession) { recoverSession("The page process stopped and was restarted.") }
            override fun onKill(killedSession: GeckoSession) { recoverSession("The page process was stopped by Android and was restarted.") }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, value: Int) {
                progress.progress = value
                progress.visibility = if (value in 1..99) View.VISIBLE else View.GONE
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                currentUrl = url ?: currentUrl
                addressBar.setText(url ?: "")
            }
            override fun onCanGoBack(session: GeckoSession, value: Boolean) { canGoBack = value; backButton.alpha = if (value) 1f else 0.35f }
            override fun onCanGoForward(session: GeckoSession, value: Boolean) { canGoForward = value; forwardButton.alpha = if (value) 1f else 0.35f }
        }
        getRuntime().webExtensionController.promptDelegate = extensionPromptDelegate
        session.open(getRuntime())
        browserView.setSession(session)
        installMediaDetector()
    }

    private fun installMediaDetector() {
        getRuntime().webExtensionController.ensureBuiltIn("resource://android/assets/media-detector/", MEDIA_DETECTOR_ID).accept(
            { extension -> extension?.let { session.getWebExtensionController().setMessageDelegate(it, mediaMessageDelegate, NATIVE_APP_NAME) } },
            { error -> android.util.Log.e("UniversalBrowser", "Media detector unavailable", error) }
        )
    }

    private val mediaMessageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            if (nativeApp != NATIVE_APP_NAME || sender.session !== session || message !is JSONObject) return null
            if (message.optString("type") != "media-playable") return null
            val url = message.optString("url").trim()
            if (!isDownloadableMediaUrl(url)) return null
            runOnUiThread { offerMediaDownload(url, message.optString("title").trim().ifBlank { "Video" }) }
            return null
        }
    }

    private fun isDownloadableMediaUrl(url: String): Boolean {
        if (url.isBlank() || url.startsWith("blob:", true) || url.startsWith("data:", true)) return false
        val lower = url.substringBefore('?').lowercase(Locale.US)
        return listOf("mp4", "webm", "mov", "m4v", "3gp", "mkv", "mp3", "m4a", "ogg", "oga").any { lower.endsWith(".$it") }
    }

    private fun offerMediaDownload(url: String, title: String) {
        val now = System.currentTimeMillis()
        if (url == lastMediaUrl && now - lastMediaPromptAt < 8_000L) return
        lastMediaUrl = url
        lastMediaPromptAt = now
        val cleanTitle = title.replace(Regex("\\s+"), " ").trim().take(80)
        AlertDialog.Builder(this).setTitle("Media ready to download")
            .setMessage("Universal detected a playable video or audio file.\n\n$cleanTitle")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Download") { _, _ -> enqueueMediaDownload(url, cleanTitle) }.show()
    }

    private fun enqueueMediaDownload(url: String, title: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(title.ifBlank { "Universal media" })
                .setDescription("Downloaded by Universal Browser")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true).setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, buildMediaFilename(url, title))
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Cookie", it) }
            if (currentUrl.isNotBlank()) request.addRequestHeader("Referer", currentUrl)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast("Download started — check Downloads for progress.")
        } catch (error: Throwable) { toast("Could not start download: ${error.message ?: "unsupported media"}") }
    }

    private fun buildMediaFilename(url: String, title: String): String {
        val path = try { Uri.parse(url).lastPathSegment.orEmpty() } catch (_: Throwable) { "" }
        val ext = Regex("\\.([A-Za-z0-9]{2,5})$").find(path.substringBefore('?'))?.groupValues?.get(1)?.lowercase(Locale.US)
        val base = title.ifBlank { path.substringBeforeLast('.', path) }.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(90).ifBlank { "Universal-media" }
        return if (ext != null && !base.lowercase(Locale.US).endsWith(".$ext")) "$base.$ext" else if (path.contains('.')) path.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120) else "$base.mp4"
    }

    private fun recoverSession(message: String) {
        runOnUiThread {
            if (!::session.isInitialized) return@runOnUiThread
            val target = currentUrl
            try { session.open(getRuntime()); if (target.isNotBlank()) session.loadUri(target); toast(message) }
            catch (_: Throwable) { showHome(); toast("Browser recovered. Please try the page again.") }
        }
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

    private fun sectionTitle(title: String, subtitle: String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply { text = title; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
        box.addView(TextView(this).apply { text = subtitle; textSize = 12f; setTextColor(muted); setPadding(0, 2.dp(), 0, 0) })
        return box
    }

    private fun quickCard(title: String, subtitle: String, mark: String, action: () -> Unit): View {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(5.dp(), 13.dp(), 5.dp(), 12.dp()); background = rounded(white, 18.dp()); elevation = 2.dp().toFloat(); setOnClickListener { action() } }
        card.addView(TextView(this).apply { text = mark; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(white); background = gradient(intArrayOf(purple, violet), 13.dp()) }, LinearLayout.LayoutParams(39.dp(), 39.dp()).apply { bottomMargin = 8.dp() })
        card.addView(TextView(this).apply { text = title; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink); gravity = Gravity.CENTER })
        card.addView(TextView(this).apply { text = subtitle; textSize = 10f; setTextColor(muted); gravity = Gravity.CENTER; setPadding(0, 2.dp(), 0, 0) })
        return card
    }

    private fun featureCard(title: String, subtitle: String, mark: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(14.dp(), 13.dp(), 12.dp(), 13.dp()); background = rounded(white, 19.dp()); elevation = 2.dp().toFloat(); setOnClickListener { action() } }
        row.addView(TextView(this).apply { text = mark; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(white); background = gradient(intArrayOf(purple, violet), 15.dp()) }, LinearLayout.LayoutParams(48.dp(), 48.dp()).apply { rightMargin = 13.dp() })
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        box.addView(TextView(this).apply { text = title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
        box.addView(TextView(this).apply { text = subtitle; textSize = 11.5f; setTextColor(muted); setPadding(0, 3.dp(), 0, 0) })
        row.addView(box); row.addView(TextView(this).apply { text = "›"; textSize = 25f; setTextColor(Color.rgb(150, 147, 166)) })
        return row
    }

    private fun showMediaDownloadInfo() {
        AlertDialog.Builder(this).setTitle("Universal Media Downloader")
            .setMessage("Universal detects supported direct media resources and hands downloads to Android's Download Manager. DRM-protected, blob-only and protected streams cannot be downloaded by this feature.")
            .setPositiveButton("Got it", null).show()
    }

    private fun showHome() { browserView.visibility = View.GONE; homePanel.visibility = View.VISIBLE; addressBar.setText(""); if (::session.isInitialized) session.setActive(false) }

    private fun navigate(raw: String) {
        val input = raw.trim()
        if (input.isEmpty()) return
        ensureBrowserReady()
        val uri = when {
            input.startsWith("http://", true) || input.startsWith("https://", true) -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(input, "UTF-8")}"
        }
        currentUrl = uri
        addressBar.setText(uri)
        homePanel.visibility = View.GONE
        browserView.visibility = View.VISIBLE
        session.setActive(true)
        session.loadUri(uri)
    }

    private fun showBrowserMenu() {
        val items = arrayOf("Home", "Extensions", "Check Chrome extension", "Browse Add-ons", "Media Downloads", "Reload", "About Universal")
        AlertDialog.Builder(this).setTitle("Universal").setItems(items) { _, which ->
            when (which) {
                0 -> showHome()
                1 -> showExtensionManagerPage()
                2 -> openExtensionPicker()
                3 -> navigate("https://addons.mozilla.org/android/")
                4 -> showMediaDownloadInfo()
                5 -> if (::session.isInitialized && browserView.visibility == View.VISIBLE) session.reload()
                6 -> showAbout()
            }
        }.show()
    }

    private fun showExtensionManagerPage() {
        getRuntime().webExtensionController.list().accept(
            { extensions -> runOnUiThread { renderExtensionManager(extensions ?: emptyList()) } },
            { error -> runOnUiThread { toast("Could not load extensions: ${error?.message ?: "unknown error"}") } }
        )
    }

    private fun renderExtensionManager(extensions: List<WebExtension>) {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18.dp(), 6.dp(), 18.dp(), 10.dp()) }
        outer.addView(TextView(this).apply { text = "Universal now scans Chrome/Firefox packages before installation."; textSize = 12.5f; setTextColor(muted); setPadding(0, 0, 0, 12.dp()) })
        if (extensions.isEmpty()) {
            val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(12.dp(), 18.dp(), 12.dp(), 18.dp()); background = rounded(surface, 18.dp()) }
            empty.addView(TextView(this).apply { text = "No extensions installed"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink); gravity = Gravity.CENTER })
            empty.addView(TextView(this).apply { text = "Use the scanner below to inspect a .crx, .xpi or ZIP WebExtension."; textSize = 12f; setTextColor(muted); gravity = Gravity.CENTER; setPadding(8.dp(), 5.dp(), 8.dp(), 12.dp()) })
            outer.addView(empty)
        } else {
            extensions.forEach { extension ->
                val name = extension.metaData.name?.takeIf { it.isNotBlank() } ?: extension.id
                val enabled = extension.metaData.enabled
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(10.dp(), 10.dp(), 6.dp(), 10.dp()); background = rounded(surface, 16.dp()) }
                row.addView(TextView(this).apply { text = "✦"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(white); background = gradient(intArrayOf(purple, violet), 12.dp()) }, LinearLayout.LayoutParams(42.dp(), 42.dp()).apply { rightMargin = 10.dp() })
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
                box.addView(TextView(this).apply { text = name; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
                box.addView(TextView(this).apply { text = if (enabled) "Enabled" else "Disabled"; textSize = 10.5f; setTextColor(if (enabled) Color.rgb(54, 139, 83) else muted) })
                row.addView(box)
                row.addView(toolbarButton(if (enabled) "OFF" else "ON", 10f) { val result = if (enabled) getRuntime().webExtensionController.disable(extension, WebExtensionController.EnableSource.USER) else getRuntime().webExtensionController.enable(extension, WebExtensionController.EnableSource.USER); result.accept({ showExtensionManagerPage() }, { error -> toast("Extension change failed: ${error?.message ?: "unknown error"}") }) })
                row.addView(toolbarButton("×", 20f) { AlertDialog.Builder(this).setTitle("Remove $name?").setMessage("This will uninstall the extension and its stored data.").setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ -> getRuntime().webExtensionController.uninstall(extension).accept({ showExtensionManagerPage() }, { error -> toast("Remove failed: ${error?.message ?: "unknown error"}") }) }.show() })
                outer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 7.dp() })
            }
        }
        val scan = TextView(this).apply { text = "＋  Scan / install Chrome or Firefox extension"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL; setTextColor(white); setPadding(18.dp(), 0, 18.dp(), 0); background = gradient(intArrayOf(purple, violet), 16.dp()); setOnClickListener { openExtensionPicker() } }
        outer.addView(scan, LinearLayout.LayoutParams(-1, 50.dp()).apply { topMargin = 12.dp() })
        outer.addView(TextView(this).apply { text = "Browse Firefox Add-ons"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(purple); setOnClickListener { navigate("https://addons.mozilla.org/android/") } }, LinearLayout.LayoutParams(-1, 45.dp()))
        AlertDialog.Builder(this).setTitle("Universal Extensions").setView(outer).setPositiveButton("Done", null).show()
    }

    private fun openExtensionPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-xpinstall", "application/x-chrome-extension", "application/zip", "application/octet-stream"))
        }, REQUEST_EXTENSION)
    }

    @Deprecated("Deprecated in Android API 33")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXTENSION && resultCode == RESULT_OK) data?.data?.let { inspectExtension(it) }
    }

    private fun inspectExtension(uri: Uri) {
        toast("Scanning extension…")
        Thread {
            try {
                val report = ExtensionCompatibilityEngine.analyzeAndPrepare(this, uri)
                runOnUiThread { showCompatibilityReport(report) }
            } catch (error: Throwable) {
                runOnUiThread { toast("Extension scan failed: ${error.message ?: "invalid package"}") }
            }
        }.start()
    }

    private fun showCompatibilityReport(report: ExtensionCompatibilityEngine.Report) {
        val levelText = when (report.level) {
            ExtensionCompatibilityEngine.Level.FULL -> "🟢 HIGH COMPATIBILITY"
            ExtensionCompatibilityEngine.Level.HIGH -> "🟢 COMPATIBILITY PREPARED"
            ExtensionCompatibilityEngine.Level.PARTIAL -> "🟡 PARTIAL COMPATIBILITY"
            ExtensionCompatibilityEngine.Level.UNSUPPORTED -> "🔴 UNSUPPORTED API DETECTED"
        }
        val details = buildString {
            append("$levelText\n\n")
            append("${report.name}  •  v${report.version}\n")
            append("Manifest V${report.manifestVersion}  •  ${report.sourceFormat}\n\n")
            report.reasons.forEach { append("• $it\n") }
        }
        val builder = AlertDialog.Builder(this).setTitle("Universal Compatibility Check").setMessage(details).setNegativeButton("Cancel", null)
        if (report.level != ExtensionCompatibilityEngine.Level.UNSUPPORTED) {
            builder.setPositiveButton("Try install") { _, _ -> installPreparedExtension(report) }
        }
        builder.show()
    }

    private fun installPreparedExtension(report: ExtensionCompatibilityEngine.Report) {
        val fileUri = Uri.fromFile(report.preparedFile).toString()
        getRuntime().webExtensionController.install(fileUri, WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
            { extension -> runOnUiThread { toast("${extension?.metaData?.name ?: report.name} installed") } },
            { error -> runOnUiThread {
                val message = error?.message ?: "Gecko rejected this package"
                AlertDialog.Builder(this).setTitle("Installation blocked").setMessage("Universal prepared the extension, but GeckoView did not accept it.\n\n$message\n\nSome Chrome extensions need a Mozilla-signed Firefox package. Universal cannot safely remove Gecko's signature requirement.").setPositiveButton("OK", null).show()
            } }
        )
    }

    private fun showAbout() {
        AlertDialog.Builder(this).setTitle("Universal Browser")
            .setMessage("A lightweight Android browser built around GeckoView and WebExtensions.\n\nThe extension system now includes a Chrome/Firefox package scanner and conservative Manifest V3 compatibility preparation.\n\nVersion 0.7.0")
            .setPositiveButton("Done", null).show()
    }

    private val extensionPromptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollectionPermissions: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse> {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            val name = extension.metaData.name?.takeIf { it.isNotBlank() } ?: extension.id
            val requested = (permissions.toList() + origins.toList() + dataCollectionPermissions.toList()).distinct().joinToString("\n").ifBlank { "No additional permissions listed." }
            runOnUiThread {
                AlertDialog.Builder(this@MainActivity).setTitle("Install $name?").setMessage("This extension is requesting:\n\n$requested")
                    .setNegativeButton("Cancel") { _, _ -> result.complete(WebExtension.PermissionPromptResponse(false, false, false)) }
                    .setPositiveButton("Install") { _, _ -> result.complete(WebExtension.PermissionPromptResponse(true, false, false)) }
                    .setOnCancelListener { result.complete(WebExtension.PermissionPromptResponse(false, false, false)) }.show()
            }
            return result
        }
    }

    private fun toolbarButton(label: String, size: Float, action: () -> Unit) = TextView(this).apply { text = label; textSize = size; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink); setOnClickListener { action() }; background = rounded(Color.TRANSPARENT, 12.dp()); layoutParams = LinearLayout.LayoutParams(40.dp(), 42.dp()) }
    private fun cardParams() = LinearLayout.LayoutParams(0, 112.dp(), 1f).apply { setMargins(4.dp(), 0, 4.dp(), 0) }
    private fun featureParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp() }
    private fun getRuntime(): GeckoRuntime = (application as UniversalBrowserApp).getRuntime()
    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun gradient(colors: IntArray, radius: Int): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius.toFloat() }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_EXTENSION = 7101
        private const val MEDIA_DETECTOR_ID = "media-detector@universalbrowser.coeric"
        private const val NATIVE_APP_NAME = "browser"
    }
}
