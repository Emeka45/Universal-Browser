package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Native GeckoView extension manager.
 *
 * GeckoView installs signed WebExtensions (.xpi) and persists them across runtime restarts.
 * Store pages are kept as discovery surfaces; Chromium CRX packages cannot be installed by
 * GeckoView's WebExtensionController and are therefore never falsely presented as compatible.
 */
object BrowserExtensionCenter {
    private var activity: Activity? = null

    fun attach(host: Activity) {
        activity = host
        val runtime = (host.application as UniversalBrowserApp).getRuntime()
        runtime.webExtensionController.setPromptDelegate(promptDelegate)
    }

    fun show(host: Activity) {
        attach(host)
        val controller = (host.application as UniversalBrowserApp).getRuntime().webExtensionController
        controller.list().accept({ extensions ->
            host.runOnUiThread { showManager(host, controller, extensions ?: emptyList()) }
        }, { error ->
            host.runOnUiThread { toast(host, "Could not read extensions: ${error?.message ?: "unknown error"}") }
        })
    }

    private fun showManager(host: Activity, controller: WebExtensionController, extensions: List<WebExtension>) {
        val items = mutableListOf<String>()
        items += "Install extension from XPI URL"
        items += "Install extension from file"
        items += "Open Firefox Add-ons"
        items += "Open Chrome Web Store"
        items += "Open Microsoft Edge Add-ons"
        items += "Open Opera Add-ons"
        if (extensions.isNotEmpty()) {
            items += "— Installed extensions —"
            extensions.forEach { ext ->
                val name = ext.metaData?.name?.takeIf { it.isNotBlank() } ?: ext.id
                val state = if (ext.metaData?.enabled == true) "Enabled" else "Disabled"
                items += "$name · $state"
            }
        }

        AlertDialog.Builder(host)
            .setTitle("Extensions")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> promptUrl(host, controller)
                    which == 1 -> chooseXpiFile(host)
                    which in 2..5 -> openStore(host, which - 2)
                    extensions.isNotEmpty() && which == 6 -> Unit
                    extensions.isNotEmpty() && which > 6 -> extensionActions(host, controller, extensions[which - 7])
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun promptUrl(host: Activity, controller: WebExtensionController) {
        val input = EditText(host).apply {
            hint = "https://…/extension.xpi"
            setSingleLine(true)
        }
        AlertDialog.Builder(host)
            .setTitle("Install signed XPI")
            .setMessage("Paste the direct HTTPS URL of a Mozilla-signed .xpi file. Store listing pages are not XPI files.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Install") { _, _ ->
                val url = input.text.toString().trim()
                if (!url.startsWith("https://", true) || !url.lowercase().contains(".xpi")) {
                    toast(host, "Enter a direct HTTPS .xpi URL")
                    return@setPositiveButton
                }
                install(host, controller, url, WebExtensionController.INSTALLATION_METHOD_MANAGER)
            }
            .show()
    }

    private fun chooseXpiFile(host: Activity) {
        try {
            host.startActivity(Intent(host, BrowserExtensionFilePickerActivity::class.java))
        } catch (_: Throwable) {
            toast(host, "No XPI file picker is available")
        }
    }

    private fun install(host: Activity, controller: WebExtensionController, uri: String, method: String) {
        controller.install(uri, method).accept({ extension ->
            host.runOnUiThread {
                val name = extension?.metaData?.name?.takeIf { it.isNotBlank() } ?: extension?.id ?: "extension"
                toast(host, "Installed $name")
            }
        }, { error ->
            host.runOnUiThread {
                toast(host, "Extension install failed: ${error?.message ?: "unsupported or invalid XPI"}")
            }
        })
    }

    private fun extensionActions(host: Activity, controller: WebExtensionController, extension: WebExtension) {
        val name = extension.metaData?.name?.takeIf { it.isNotBlank() } ?: extension.id
        val enabled = extension.metaData?.enabled == true
        val actions = if (enabled) arrayOf("Disable", "Uninstall") else arrayOf("Enable", "Uninstall")
        AlertDialog.Builder(host)
            .setTitle(name)
            .setItems(actions) { _, which ->
                if (which == 0) {
                    val result = if (enabled) controller.disable(extension, WebExtensionController.EnableSource.USER)
                    else controller.enable(extension, WebExtensionController.EnableSource.USER)
                    result.accept({ toast(host, if (enabled) "Extension disabled" else "Extension enabled") }, { e -> toast(host, "Could not change extension: ${e?.message ?: "unknown error"}") })
                } else {
                    AlertDialog.Builder(host)
                        .setTitle("Uninstall $name?")
                        .setMessage("This removes the extension and its stored extension data.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Uninstall") { _, _ ->
                            controller.uninstall(extension).accept({ toast(host, "Extension uninstalled") }, { e -> toast(host, "Could not uninstall: ${e?.message ?: "unknown error"}") })
                        }.show()
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun openStore(host: Activity, index: Int) {
        val urls = arrayOf(
            "https://addons.mozilla.org/android/",
            "https://chromewebstore.google.com/",
            "https://microsoftedge.microsoft.com/addons/",
            "https://addons.opera.com/"
        )
        try {
            host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urls[index])))
        } catch (_: Throwable) {
            toast(host, "No browser available to open this store")
        }
    }

    private val promptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>
        ): GeckoResult<WebExtension.PermissionPromptResponse>? {
            val host = activity ?: return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(false, false, false))
            val name = extension.metaData?.name?.takeIf { it.isNotBlank() } ?: extension.id
            val permissionText = (permissions.toList() + origins.toList() + dataCollectionPermissions.toList())
                .distinct().take(20).joinToString("\n") { "• $it" }
                .ifBlank { "No additional permissions were reported." }
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            host.runOnUiThread {
                AlertDialog.Builder(host)
                    .setTitle("Install $name?")
                    .setMessage("This extension requests:\n\n$permissionText")
                    .setNegativeButton("Cancel") { _, _ -> result.complete(WebExtension.PermissionPromptResponse(false, false, false)) }
                    .setPositiveButton("Install") { _, _ -> result.complete(WebExtension.PermissionPromptResponse(true, true, false)) }
                    .setOnCancelListener { result.complete(WebExtension.PermissionPromptResponse(false, false, false)) }
                    .show()
            }
            return result
        }
    }

    private fun toast(host: Activity, message: String) {
        host.runOnUiThread { Toast.makeText(host, message, Toast.LENGTH_LONG).show() }
    }
}
