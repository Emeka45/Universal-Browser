package com.coeric.universalbrowser

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : Activity() {
    private lateinit var session: GeckoSession
    private lateinit var addressBar: EditText
    private lateinit var progress: ProgressBar
    private var canGoBack = false
    private var canGoForward = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = getRuntime()
        session = GeckoSession()
        session.open(runtime)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 6, 8, 3)
        }

        val back = button("‹") { if (canGoBack) session.goBack() }
        val forward = button("›") { if (canGoForward) session.goForward() }
        toolbar.addView(back)
        toolbar.addView(forward)
        toolbar.addView(button("↻") { session.reload() })
        toolbar.addView(button("U") { loadHome() })

        addressBar = EditText(this).apply {
            hint = "Search or enter address"
            setSingleLine(true)
            setPadding(16, 0, 16, 0)
            setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true }
        }
        toolbar.addView(addressBar, LinearLayout.LayoutParams(0, 48.dp(), 1f))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }

        val geckoView = GeckoView(this)
        geckoView.setSession(session)
        root.addView(toolbar)
        root.addView(progress, LinearLayout.LayoutParams(-1, 3.dp()))
        root.addView(geckoView, LinearLayout.LayoutParams(-1, 0, 1f))
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
                back.isEnabled = value
            }

            override fun onCanGoForward(session: GeckoSession, value: Boolean) {
                canGoForward = value
                forward.isEnabled = value
            }
        }
        if (savedInstanceState == null) loadHome()
    }

    private fun getRuntime(): GeckoRuntime = synchronized(RUNTIME_LOCK) {
        runtime ?: GeckoRuntime.create(applicationContext).also { runtime = it }
    }

    private fun loadHome() = navigate("https://www.google.com")

    private fun navigate(raw: String) {
        val input = raw.trim()
        if (input.isEmpty()) return
        val uri = when {
            input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true) -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(input, "UTF-8")}"
        }
        addressBar.setText(uri)
        session.loadUri(uri)
    }

    private fun button(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = if (label == "U") 18f else 28f
        gravity = Gravity.CENTER
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(44.dp(), 48.dp())
    }

    override fun onBackPressed() {
        if (canGoBack) session.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_OPEN = 42
        private val RUNTIME_LOCK = Any()
        @Volatile private var runtime: GeckoRuntime? = null
    }
}
