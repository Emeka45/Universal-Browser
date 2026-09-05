package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
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
    private var canGoBack = false
    private var canGoForward = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = getRuntime()
        runtime.webExtensionController.promptDelegate = extensionPromptDelegate
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
        toolbar.addView(button("E") { openExtensionPicker() })
        toolbar.addView(button("≡") { showExtensionManager() })

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

    private fun openExtensionPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-xpinstall"
        }, REQUEST_EXTENSION)
    }

    private fun showExtensionManager() {
        getRuntime().webExtensionController.list().accept(
            { extensions -> runOnUiThread { renderExtensionManager(extensions ?: emptyList()) } },
            { error -> runOnUiThread { Toast.makeText(this, "Could not load extensions: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show() } }
        )
    }

    private fun renderExtensionManager(extensions: List<WebExtension>) {
        if (extensions.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Extensions")
                .setMessage("No extensions installed yet.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
        }
        extensions.forEach { extension ->
            val name = extension.metaData.name.takeIf { it.isNotBlank() } ?: extension.id
            val enabled = extension.metaData.enabled
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply {
                text = "$name\n${if (enabled) "Enabled" else "Disabled"}"
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(label)
            row.addView(button(if (enabled) "OFF" else "ON") {
                val controller = getRuntime().webExtensionController
                val result = if (enabled) {
                    controller.disable(extension, WebExtensionController.EnableSource.USER)
                } else {
                    controller.enable(extension, WebExtensionController.EnableSource.USER)
                }
                result.accept(
                    { showExtensionManager() },
                    { error -> Toast.makeText(this, "Extension change failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show() }
                )
            })
            row.addView(button("×") {
                AlertDialog.Builder(this)
                    .setTitle("Remove $name?")
                    .setMessage("This will uninstall the extension and remove its stored data.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove") { _, _ ->
                        getRuntime().webExtensionController.uninstall(extension).accept(
                            { showExtensionManager() },
                            { error -> Toast.makeText(this, "Remove failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show() }
                        )
                    }
                    .show()
            })
            container.addView(row)
        }

        AlertDialog.Builder(this)
            .setTitle("Extensions")
            .setView(container)
            .setPositiveButton("Done", null)
            .show()
    }

    @Deprecated("Deprecated in Android API 33")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXTENSION && resultCode == RESULT_OK) {
            data?.data?.let { installExtension(it) }
        }
    }

    private fun installExtension(uri: android.net.Uri) {
        val controller = getRuntime().webExtensionController
        controller.install(uri.toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE)
            .accept(
                { extension ->
                    runOnUiThread {
                        val name = extension?.metaData?.name?.takeIf { it.isNotBlank() } ?: extension?.id ?: "extension"
                        Toast.makeText(this, "$name installed", Toast.LENGTH_LONG).show()
                    }
                },
                { error ->
                    runOnUiThread {
                        Toast.makeText(this, "Extension install failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    }
                }
            )
    }

    private val extensionPromptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            val name = extension.metaData.name.takeIf { it.isNotBlank() } ?: extension.id
            val requested = (permissions.toList() + origins.toList() + dataCollectionPermissions.toList())
                .distinct()
                .joinToString("\n")
                .ifBlank { "No additional permissions requested." }
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    result.complete(WebExtension.PermissionPromptResponse(false, false, false))
                    return@runOnUiThread
                }
                AlertDialog.Builder(this)
                    .setTitle("Install $name?")
                    .setMessage("Requested permissions:\n\n$requested")
                    .setNegativeButton("Cancel") { _, _ ->
                        result.complete(WebExtension.PermissionPromptResponse(false, false, false))
                    }
                    .setPositiveButton("Install") { _, _ ->
                        result.complete(WebExtension.PermissionPromptResponse(true, false, false))
                    }
                    .setOnCancelListener {
                        result.complete(WebExtension.PermissionPromptResponse(false, false, false))
                    }
                    .show()
            }
            return result
        }
    }

    private fun button(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = if (label == "U") 18f else 22f
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
        private const val REQUEST_EXTENSION = 43
        private val RUNTIME_LOCK = Any()
        @Volatile private var runtime: GeckoRuntime? = null
    }
}
