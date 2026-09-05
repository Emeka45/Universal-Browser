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

        session = GeckoSession()
        val runtime = getRuntime()
        runtime.webExtensionController.promptDelegate = extensionPromptDelegate
        session.open(runtime)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(white)
        }

        root.addView(buildToolbar())
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
            setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
            setBackgroundColor(white)
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }

        backButton = toolbarButton("‹", 28f) { if (canGoBack) session.goBack() }
        forwardButton = toolbarButton("›", 28f) { if (canGoForward) session.goForward() }
        row.addView(backButton)
        row.addView(forwardButton)

        val brand = TextView(this).apply {
            text = "U"
            textSize = 19f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(white)
            background = gradient(intArrayOf(violet, purple, darkPurple), 13.dp())
            elevation = 4.dp().toFloat()
        }
        row.addView(brand, LinearLayout.LayoutParams(39.dp(), 39.dp()).apply { setMargins(3.dp(), 0, 7.dp(), 0) })

        addressBar = EditText(this).apply {
            hint = "Search or enter address"
            textSize = 14.5f
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(Color.rgb(145, 143, 158))
            setPadding(16.dp(), 0, 14.dp(), 0)
            background = rounded(surface, 22.dp())
            setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true }
        }
        row.addView(addressBar, LinearLayout.LayoutParams(0, 44.dp(), 1f))
        row.addView(toolbarButton("↻", 21f) { if (browserView.visibility == View.VISIBLE) session.reload() else showHome() })
        row.addView(toolbarButton("⋮", 23f) { showBrowserMenu() })
        outer.addView(row)
        return outer
    }

    private fun buildHomePanel(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(surface) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 34.dp())
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22.dp(), 28.dp(), 22.dp(), 28.dp())
            background = gradient(intArrayOf(darkPurple, purple, violet), 28.dp())
            elevation = 5.dp().toFloat()
        }
        val logo = TextView(this).apply {
            text = "U"
            textSize = 58f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(white)
            background = gradient(intArrayOf(violet, purple), 31.dp())
            elevation = 7.dp().toFloat()
        }
        hero.addView(logo, LinearLayout.LayoutParams(104.dp(), 104.dp()).apply { bottomMargin = 17.dp() })
        hero.addView(TextView(this).apply {
            text = "UNIVERSAL"
            textSize = 29f
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(white)
            gravity = Gravity.CENTER
        })
        hero.addView(TextView(this).apply {
            text = "Your web. Your way."
            textSize = 15f
            setTextColor(Color.rgb(235, 231, 255))
            gravity = Gravity.CENTER
            setPadding(0, 4.dp(), 0, 20.dp())
        })

        val heroSearch = EditText(this).apply {
            hint = "Search the web or enter a URL"
            textSize = 15f
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(Color.rgb(125, 123, 140))
            setPadding(18.dp(), 0, 18.dp(), 0)
            background = rounded(white, 19.dp())
            elevation = 5.dp().toFloat()
            setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true }
        }
        hero.addView(heroSearch, LinearLayout.LayoutParams(-1, 54.dp()))
        content.addView(hero)

        content.addView(sectionTitle("Quick access", "Jump back into what matters"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 24.dp(); bottomMargin = 10.dp() })
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(quickCard("Google", "Search", "G") { navigate("https://www.google.com") }, cardParams())
        quick.addView(quickCard("YouTube", "Watch", "▶") { navigate("https://www.youtube.com") }, cardParams())
        quick.addView(quickCard("Wikipedia", "Explore", "W") { navigate("https://www.wikipedia.org") }, cardParams())
        content.addView(quick)

        content.addView(sectionTitle("Your browser", "Everything you need, without the clutter"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 22.dp(); bottomMargin = 10.dp() })
        val extensions = featureCard("Extensions", "Install, manage and remove WebExtensions", "▣") { showExtensionManagerPage() }
        content.addView(extensions)
        val addons = featureCard("Add-ons", "Browse Firefox-compatible extensions", "+") { navigate("https://addons.mozilla.org/android/") }
        content.addView(addons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp() })
        val privacy = featureCard("Private by design", "Fast, focused browsing with a lightweight interface", "◈") { showAbout() }
        content.addView(privacy, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp() })

        content.addView(TextView(this).apply {
            text = "UNIVERSAL BROWSER  •  BUILT FOR ANDROID"
            textSize = 10f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(155, 152, 168))
            gravity = Gravity.CENTER
            setPadding(0, 26.dp(), 0, 0)
        })
        scroll.addView(content)
        return scroll
    }

    private fun sectionTitle(title: String, subtitle: String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply {
            text = title
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
        })
        box.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(muted)
            setPadding(0, 2.dp(), 0, 0)
        })
        return box
    }

    private fun quickCard(title: String, subtitle: String, mark: String, action: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(5.dp(), 13.dp(), 5.dp(), 12.dp())
            background = rounded(white, 18.dp())
            elevation = 2.dp().toFloat()
            setOnClickListener { action() }
        }
        val icon = TextView(this).apply {
            text = mark
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(white)
            background = gradient(intArrayOf(purple, violet), 13.dp())
        }
        card.addView(icon, LinearLayout.LayoutParams(39.dp(), 39.dp()).apply { bottomMargin = 8.dp() })
        card.addView(TextView(this).apply { text = title; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink); gravity = Gravity.CENTER })
        card.addView(TextView(this).apply { text = subtitle; textSize = 10f; setTextColor(muted); gravity = Gravity.CENTER; setPadding(0, 2.dp(), 0, 0) })
        return card
    }

    private fun featureCard(title: String, subtitle: String, mark: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 13.dp(), 12.dp(), 13.dp())
            background = rounded(white, 19.dp())
            elevation = 2.dp().toFloat()
            setOnClickListener { action() }
        }
        val icon = TextView(this).apply {
            text = mark
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(white)
            background = gradient(intArrayOf(purple, violet), 15.dp())
        }
        row.addView(icon, LinearLayout.LayoutParams(48.dp(), 48.dp()).apply { rightMargin = 13.dp() })
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        textBox.addView(TextView(this).apply { text = title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
        textBox.addView(TextView(this).apply { text = subtitle; textSize = 11.5f; setTextColor(muted); setPadding(0, 3.dp(), 0, 0) })
        row.addView(textBox)
        row.addView(TextView(this).apply { text = "›"; textSize = 25f; setTextColor(Color.rgb(150, 147, 166)) })
        return row
    }

    private fun cardParams() = LinearLayout.LayoutParams(0, 112.dp(), 1f).apply { setMargins(4.dp(), 0, 4.dp(), 0) }

    private fun toolbarButton(label: String, size: Float, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = size
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ink)
        setOnClickListener { action() }
        background = rounded(Color.TRANSPARENT, 12.dp())
        layoutParams = LinearLayout.LayoutParams(40.dp(), 42.dp())
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
        val items = arrayOf("Home", "Extensions", "Browse Add-ons", "Reload", "About Universal")
        AlertDialog.Builder(this).setTitle("Universal").setItems(items) { _, which ->
            when (which) {
                0 -> showHome()
                1 -> showExtensionManagerPage()
                2 -> navigate("https://addons.mozilla.org/android/")
                3 -> if (browserView.visibility == View.VISIBLE) session.reload()
                4 -> showAbout()
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
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 6.dp(), 18.dp(), 10.dp())
        }
        val intro = TextView(this).apply {
            text = "Manage the WebExtensions installed in Universal Browser."
            textSize = 12.5f
            setTextColor(muted)
            setPadding(0, 0, 0, 12.dp())
        }
        outer.addView(intro)

        if (extensions.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(12.dp(), 18.dp(), 12.dp(), 18.dp())
                background = rounded(surface, 18.dp())
            }
            empty.addView(TextView(this).apply {
                text = "No extensions installed"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ink)
                gravity = Gravity.CENTER
            })
            empty.addView(TextView(this).apply {
                text = "Install a Firefox-compatible .xpi file or browse the add-ons store."
                textSize = 12f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(8.dp(), 5.dp(), 8.dp(), 12.dp())
            })
            outer.addView(empty)
        } else {
            extensions.forEach { extension ->
                val name = extension.metaData.name?.takeIf { it.isNotBlank() } ?: extension.id
                val enabled = extension.metaData.enabled
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(10.dp(), 10.dp(), 6.dp(), 10.dp())
                    background = rounded(surface, 16.dp())
                }
                val icon = TextView(this).apply {
                    text = "✦"
                    textSize = 17f
                    gravity = Gravity.CENTER
                    setTextColor(white)
                    background = gradient(intArrayOf(purple, violet), 12.dp())
                }
                row.addView(icon, LinearLayout.LayoutParams(42.dp(), 42.dp()).apply { rightMargin = 10.dp() })
                val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
                textBox.addView(TextView(this).apply { text = name; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
                textBox.addView(TextView(this).apply { text = if (enabled) "Enabled" else "Disabled"; textSize = 10.5f; setTextColor(if (enabled) Color.rgb(54, 139, 83) else muted); setPadding(0, 2.dp(), 0, 0) })
                row.addView(textBox)
                row.addView(toolbarButton(if (enabled) "OFF" else "ON", 10f) {
                    val controller = getRuntime().webExtensionController
                    val result = if (enabled) controller.disable(extension, WebExtensionController.EnableSource.USER) else controller.enable(extension, WebExtensionController.EnableSource.USER)
                    result.accept({ showExtensionManagerPage() }, { error -> toast("Extension change failed: ${error?.message ?: "unknown error"}") })
                })
                row.addView(toolbarButton("×", 20f) {
                    AlertDialog.Builder(this).setTitle("Remove $name?").setMessage("This will uninstall the extension and its stored data.").setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ ->
                        getRuntime().webExtensionController.uninstall(extension).accept({ showExtensionManagerPage() }, { error -> toast("Remove failed: ${error?.message ?: "unknown error"}") })
                    }.show()
                })
                outer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 7.dp() })
            }
        }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12.dp(), 0, 0) }
        val install = TextView(this).apply {
            text = "＋  Install extension from device"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(white)
            setPadding(18.dp(), 0, 18.dp(), 0)
            background = gradient(intArrayOf(purple, violet), 16.dp())
            setOnClickListener { openExtensionPicker() }
        }
        actions.addView(install, LinearLayout.LayoutParams(-1, 50.dp()))
        val browse = TextView(this).apply {
            text = "Browse Firefox Add-ons"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(purple)
            setOnClickListener { navigate("https://addons.mozilla.org/android/") }
        }
        actions.addView(browse, LinearLayout.LayoutParams(-1, 45.dp()))
        outer.addView(actions)

        AlertDialog.Builder(this).setTitle("Extensions").setView(outer).setPositiveButton("Done", null).show()
    }

    private fun openExtensionPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-xpinstall"
        }, REQUEST_EXTENSION)
    }

    @Deprecated("Deprecated in Android API 33")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXTENSION && resultCode == RESULT_OK) data?.data?.let { installExtension(it) }
    }

    private fun installExtension(uri: Uri) {
        getRuntime().webExtensionController.install(uri.toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
            { extension -> runOnUiThread { toast("${extension?.metaData?.name ?: "Extension"} installed"); showExtensionManagerPage() } },
            { error -> runOnUiThread { toast("Extension install failed: ${error?.message ?: "unknown error"}") } }
        )
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Universal Browser")
            .setMessage("A lightweight Android browser built around GeckoView and WebExtensions.\n\nVersion 0.5.0\n\nDesigned to stay clean, fast and extension-ready on everyday Android devices.")
            .setPositiveButton("Done", null)
            .show()
    }

    private val extensionPromptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollectionPermissions: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse> {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            val name = extension.metaData.name?.takeIf { it.isNotBlank() } ?: extension.id
            val requested = (permissions.toList() + origins.toList() + dataCollectionPermissions.toList()).distinct().joinToString("\n").ifBlank { "No additional permissions listed." }
            runOnUiThread {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Install $name?")
                    .setMessage("This extension is requesting:\n\n$requested")
                    .setNegativeButton("Cancel") { _, _ -> result.complete(WebExtension.PermissionPromptResponse(false, false, false)) }
                    .setPositiveButton("Install") { _, _ -> result.complete(WebExtension.PermissionPromptResponse(true, false, false)) }
                    .setOnCancelListener { _ -> result.complete(WebExtension.PermissionPromptResponse(false, false, false)) }
                    .show()
            }
            return result
        }
    }

    private fun getRuntime(): GeckoRuntime = (application as UniversalBrowserApp).runtime

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun gradient(colors: IntArray, radius: Int): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
        cornerRadius = radius.toFloat()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_EXTENSION = 7101
    }
}
