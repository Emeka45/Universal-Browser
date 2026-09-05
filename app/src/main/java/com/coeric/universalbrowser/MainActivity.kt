package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

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

    private val purple = Color.rgb(98, 72, 255)
    private val darkPurple = Color.rgb(55, 38, 145)
    private val ink = Color.rgb(24, 24, 38)
    private val muted = Color.rgb(105, 103, 125)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(246, 244, 255)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        session = GeckoSession()
        val runtime = getRuntime()
        runtime.webExtensionController.promptDelegate = extensionPromptDelegate
        session.open(runtime)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val toolbar = buildToolbar()
        root.addView(toolbar)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(purple)
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, 3.dp()))

        browserView = GeckoView(this).apply { setSession(this@MainActivity.session) }
        homePanel = buildHomePanel()
        root.addView(homePanel, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(browserView, LinearLayout.LayoutParams(-1, 0, 1f))
        browserView.visibility = View.GONE
        setContentView(root)

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, value: Int) {
                progress.progress = value
                progress.visibility = if (value in 1..99) View.VISIBLE else View.GONE
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                addressBar.setText(url ?: "")
            }

            override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                canGoBack = value
                backButton.alpha = if (value) 1f else 0.35f
            }

            override fun onCanGoForward(session: GeckoSession, value: Boolean) {
                canGoForward = value
                forwardButton.alpha = if (value) 1f else 0.35f
            }
        }
    }

    private fun buildToolbar(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 8.dp(), 8.dp(), 7.dp())
            background = rounded(Color.WHITE, 0, 0, 0, 18.dp())
            elevation = 5.dp().toFloat()
        }

        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        backButton = toolbarButton("‹", 28f) { if (canGoBack) session.goBack() }
        forwardButton = toolbarButton("›", 28f) { if (canGoForward) session.goForward() }
        row.addView(backButton)
        row.addView(forwardButton)

        val brand = TextView(this).apply {
            text = "U"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(purple, darkPurple)).apply {
                cornerRadius = 12.dp().toFloat()
            }
            elevation = 3.dp().toFloat()
        }
        row.addView(brand, LinearLayout.LayoutParams(38.dp(), 38.dp()).apply { setMargins(3.dp(), 0, 7.dp(), 0) })

        addressBar = EditText(this).apply {
            hint = "Search or enter address"
            textSize = 15f
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(Color.rgb(150, 148, 166))
            setPadding(14.dp(), 0, 14.dp(), 0)
            background = rounded(Color.rgb(246, 244, 251), 0, 0, 0, 22.dp())
            setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true }
        }
        row.addView(addressBar, LinearLayout.LayoutParams(0, 44.dp(), 1f))

        row.addView(toolbarButton("↻", 22f) { if (browserView.visibility == View.VISIBLE) session.reload() else showHome() })
        row.addView(toolbarButton("⌄", 22f) { showBrowserMenu() })
        outer.addView(row)
        return outer
    }

    private fun buildHomePanel(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(249, 248, 253)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp(), 34.dp(), 24.dp(), 32.dp())
        }

        val logo = TextView(this).apply {
            text = "U"
            textSize = 62f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(purple, Color.rgb(142, 75, 255), darkPurple)).apply {
                cornerRadius = 34.dp().toFloat()
            }
            elevation = 10.dp().toFloat()
        }
        content.addView(logo, LinearLayout.LayoutParams(118.dp(), 118.dp()).apply { bottomMargin = 20.dp() })

        content.addView(TextView(this).apply {
            text = "Universal"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))

        content.addView(TextView(this).apply {
            text = "A faster, cleaner way to explore the web."
            textSize = 15f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 4.dp(), 0, 22.dp())
        }, LinearLayout.LayoutParams(-1, -2))

        val search = EditText(this).apply {
            hint = "What do you want to find?"
            textSize = 16f
            setSingleLine(true)
            setPadding(18.dp(), 0, 18.dp(), 0)
            setTextColor(ink)
            background = rounded(Color.WHITE, 0, 0, 0, 18.dp())
            elevation = 4.dp().toFloat()
            setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true }
        }
        content.addView(search, LinearLayout.LayoutParams(-1, 56.dp()).apply { bottomMargin = 24.dp() })

        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        cards.addView(homeCard("Extensions", "Add WebExtensions") { showExtensionHub() }, weightParams())
        cards.addView(homeCard("Chatwait", "Open assistant") { installChatwait() }, weightParams())
        cards.addView(homeCard("Browse", "Open the web") { navigate("https://www.google.com") }, weightParams())
        content.addView(cards)

        content.addView(TextView(this).apply {
            text = "Built for Android • Lightweight • Extension-ready"
            textSize = 12f
            setTextColor(Color.rgb(140, 138, 155))
            gravity = Gravity.CENTER
            setPadding(0, 28.dp(), 0, 0)
        })

        scroll.addView(content)
        return scroll
    }

    private fun homeCard(title: String, subtitle: String, action: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(6.dp(), 14.dp(), 6.dp(), 14.dp())
            background = rounded(Color.WHITE, 0, 0, 0, 18.dp())
            elevation = 3.dp().toFloat()
            setOnClickListener { action() }
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
            gravity = Gravity.CENTER
        })
        card.addView(TextView(this).apply {
            text = subtitle
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 3.dp(), 0, 0)
        })
        return card
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, 88.dp(), 1f).apply { setMargins(4.dp(), 0, 4.dp(), 0) }

    private fun toolbarButton(label: String, size: Float, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = size
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ink)
        setOnClickListener { action() }
        background = rounded(Color.TRANSPARENT, 0, 0, 0, 12.dp())
        layoutParams = LinearLayout.LayoutParams(42.dp(), 42.dp())
    }

    private fun showHome() {
        browserView.visibility = View.GONE
        homePanel.visibility = View.VISIBLE
        addressBar.setText("")
    }

    private fun navigate(raw: String) {
        val input = raw.trim()
        if (input.isEmpty()) return
        val uri = when {
            input.startsWith("http://", true) || input.startsWith("https://", true) -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(input, "UTF-8")}"
        }
        addressBar.setText(uri)
        homePanel.visibility = View.GONE
        browserView.visibility = View.VISIBLE
        session.loadUri(uri)
    }

    private fun showBrowserMenu() {
        val items = arrayOf("Home", "Extensions", "Firefox Add-ons", "Reload", "About Universal")
        AlertDialog.Builder(this)
            .setTitle("Universal")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showHome()
                    1 -> showExtensionHub()
                    2 -> navigate("https://addons.mozilla.org/android/")
                    3 -> if (browserView.visibility == View.VISIBLE) session.reload()
                    4 -> AlertDialog.Builder(this).setTitle("Universal Browser").setMessage("Universal Browser 0.5\nLightweight Android browser with WebExtension support.").setPositiveButton("OK", null).show()
                }
            }.show()
    }

    private fun showExtensionHub() {
        AlertDialog.Builder(this)
            .setTitle("Extensions")
            .setItems(arrayOf("Installed extensions", "Install XPI from device", "Browse Firefox Add-ons")) { _, which ->
                when (which) {
                    0 -> showExtensionManager()
                    1 -> openExtensionPicker()
                    2 -> navigate("https://addons.mozilla.org/android/")
                }
            }.show()
    }

    private fun openExtensionPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-xpinstall"
        }, REQUEST_EXTENSION)
    }

    private fun showExtensionManager() {
        getRuntime().webExtensionController.list().accept(
            { extensions -> runOnUiThread { renderExtensionManager(extensions ?: emptyList()) } },
            { error -> runOnUiThread { toast("Could not load extensions: ${error?.message ?: "unknown error"}") } }
        )
    }

    private fun renderExtensionManager(extensions: List<WebExtension>) {
        if (extensions.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Installed extensions").setMessage("No extensions installed yet.\n\nYou can install a Firefox-compatible .xpi file or browse Firefox Add-ons.").setPositiveButton("OK", null).show()
            return
        }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18.dp(), 6.dp(), 18.dp(), 6.dp()) }
        extensions.forEach { extension ->
            val name = extension.metaData.name?.takeIf { it.isNotBlank() } ?: extension.id
            val enabled = extension.metaData.enabled
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 8.dp(), 0, 8.dp()) }
            row.addView(TextView(this).apply {
                text = "$name\n${if (enabled) "Enabled" else "Disabled"}"
                textSize = 15f
                setTextColor(ink)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            row.addView(toolbarButton(if (enabled) "OFF" else "ON", 11f) {
                val controller = getRuntime().webExtensionController
                val result = if (enabled) controller.disable(extension, WebExtensionController.EnableSource.USER) else controller.enable(extension, WebExtensionController.EnableSource.USER)
                result.accept({ showExtensionManager() }, { error -> toast("Extension change failed: ${error?.message ?: "unknown error"}") })
            })
            row.addView(toolbarButton("×", 22f) {
                AlertDialog.Builder(this).setTitle("Remove $name?").setMessage("This will uninstall the extension and remove its stored data.").setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ ->
                    getRuntime().webExtensionController.uninstall(extension).accept({ showExtensionManager() }, { error -> toast("Remove failed: ${error?.message ?: "unknown error"}") })
                }.show()
            })
            container.addView(row)
        }
        AlertDialog.Builder(this).setTitle("Installed extensions").setView(container).setPositiveButton("Done", null).show()
    }

    private fun installChatwait() {
        val controller = getRuntime().webExtensionController
        controller.list().accept({ extensions ->
            if (extensions?.any { it.id == CHATWAIT_EXTENSION_ID } == true) {
                runOnUiThread { toast("Chatwait is already installed") }
                return@accept
            }
            controller.install(CHATWAIT_AMO_XPI_URL, WebExtensionController.INSTALLATION_METHOD_MANAGER).accept(
                { extension -> runOnUiThread { toast("${extension?.metaData?.name ?: "Chatwait"} installed") } },
                { error -> runOnUiThread { toast("Chatwait install failed: ${error?.message ?: "unknown error"}") } }
            )
        }, { error -> runOnUiThread { toast("Could not check extensions: ${error?.message ?: "unknown error"}") } })
    }

    @Deprecated("Deprecated in Android API 33")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXTENSION && resultCode == RESULT_OK) data?.data?.let { installExtension(it) }
    }

    private fun installExtension(uri: Uri) {
        getRuntime().webExtensionController.install(uri.toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
            { extension -> runOnUiThread { toast("${extension?.metaData?.name ?: "Extension"} installed") } },
            { error -> runOnUiThread { toast("Extension install failed: ${error?.message ?: "unknown error"}") } }
        )
    }

    private val extensionPromptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollectionPermissions: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse> {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            val name = extension.metaData.name?.takeIf { it.isNotBlank() } ?: extension.id
            val requested = (permissions.toList() + origins.toList() + dataCollectionPermissions.toList()).distinct().joinToString("\n").ifBlank { "No additional permissions requested." }
            runOnUiThread {
                if (isFinishing || isDestroyed) { result.complete(WebExtension.PermissionPromptResponse(false, false, false)); return@runOnUiThread }
                AlertDialog.Builder(this@MainActivity).setTitle("Install $name?").setMessage("Requested permissions:\n\n$requested").setNegativeButton("Cancel") { _, _ -> result.complete(WebExtension.PermissionPromptResponse(false, false, false)) }.setPositiveButton("Install") { _, _ -> result.complete(WebExtension.PermissionPromptResponse(true, false, false)) }.setOnCancelListener { result.complete(WebExtension.PermissionPromptResponse(false, false, false)) }.show()
            }
            return result
        }
    }

    private fun getRuntime(): GeckoRuntime = synchronized(RUNTIME_LOCK) { runtime ?: GeckoRuntime.create(applicationContext).also { runtime = it } }

    private fun rounded(color: Int, strokeColor: Int, strokeWidth: Int, padding: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        cornerRadius = radius.toFloat()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Android API 33")
    override fun onBackPressed() {
        if (browserView.visibility == View.VISIBLE && canGoBack) session.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_EXTENSION = 43
        private const val CHATWAIT_EXTENSION_ID = "extension@chatwait.com"
        private const val CHATWAIT_AMO_XPI_URL = "https://addons.mozilla.org/firefox/downloads/latest/chatwait/latest.xpi"
        private val RUNTIME_LOCK = Any()
        @Volatile private var runtime: GeckoRuntime? = null
    }
}
