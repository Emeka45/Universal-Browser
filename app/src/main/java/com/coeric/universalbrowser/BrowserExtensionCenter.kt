package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** WebExtension installer and management center for GeckoView-compatible extensions. */
object BrowserExtensionCenter {
    private const val PICK_EXTENSION = 7101

    fun openInstaller(activity: Activity) {
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-xpinstall", "application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }, PICK_EXTENSION)
    }

    fun handleResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?, runtime: GeckoRuntime): Boolean {
        if (requestCode != PICK_EXTENSION) return false
        if (resultCode == Activity.RESULT_OK) data?.data?.let { install(activity, it, runtime) }
        return true
    }

    fun showManager(activity: Activity, runtime: GeckoRuntime) {
        runtime.webExtensionController.list().accept({ extensions -> activity.runOnUiThread {
            val list = extensions ?: emptyList()
            if (list.isEmpty()) {
                AlertDialog.Builder(activity)
                    .setTitle("Extensions")
                    .setMessage("No extensions installed yet.\n\nUniversal Browser uses GeckoView WebExtensions. Firefox-compatible extensions are the best fit; Chrome-only APIs may not work.")
                    .setNegativeButton("Close", null)
                    .setPositiveButton("Install file") { _, _ -> openInstaller(activity) }
                    .show()
                return@runOnUiThread
            }
            val labels = list.map { extensionLabel(it) }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle("Extensions (${list.size})")
                .setItems(labels) { _, which -> showExtensionActions(activity, runtime, list[which]) }
                .setNeutralButton("Install file") { _, _ -> openInstaller(activity) }
                .setNegativeButton("Close", null)
                .show()
        } }, { error -> Toast.makeText(activity, "Could not list extensions: ${error?.message ?: "unknown error"}", Toast.LENGTH_SHORT).show() })
    }

    private fun showExtensionActions(activity: Activity, runtime: GeckoRuntime, extension: WebExtension) {
        val enabled = extension.metaData.enabled
        val title = extension.metaData.name ?: extension.id
        val actions = arrayOf(if (enabled) "Disable" else "Enable", "Allow in private browsing", "Uninstall", "Compatibility details")
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage("${extension.metaData.description ?: "No description"}\n\nVersion: ${extension.metaData.version ?: "unknown"}\nID: ${extension.id}")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        val result = if (enabled) {
                            runtime.webExtensionController.disable(extension, WebExtensionController.EnableSource.USER)
                        } else {
                            runtime.webExtensionController.enable(extension, WebExtensionController.EnableSource.USER)
                        }
                        result.accept({ _ -> showManager(activity, runtime) }, { _ -> Toast.makeText(activity, "Extension action failed", Toast.LENGTH_SHORT).show() })
                    }
                    1 -> runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true)
                        .accept({ _ -> Toast.makeText(activity, "Private browsing permission updated", Toast.LENGTH_SHORT).show() }, { _ -> Toast.makeText(activity, "Could not update private permission", Toast.LENGTH_SHORT).show() })
                    2 -> AlertDialog.Builder(activity)
                        .setTitle("Remove extension?")
                        .setMessage("Remove $title from Universal Browser?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove") { _, _ ->
                            runtime.webExtensionController.uninstall(extension)
                                .accept({ _ -> showManager(activity, runtime) }, { _ -> Toast.makeText(activity, "Uninstall failed", Toast.LENGTH_SHORT).show() })
                        }.show()
                    3 -> Toast.makeText(activity, "Compatibility is checked before installation. Unsupported Chrome-only APIs may prevent an extension from working fully.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun extensionLabel(extension: WebExtension): String {
        val state = if (extension.metaData.enabled) "Enabled" else "Disabled"
        return "${extension.metaData.name ?: extension.id} • ${extension.metaData.version ?: "?"} • $state"
    }

    private fun install(activity: Activity, uri: Uri, runtime: GeckoRuntime) {
        try {
            val report = ExtensionCompatibilityEngine.analyzeAndPrepare(activity, uri)
            val details = buildString {
                append(report.name).append(" ").append(report.version)
                append("\nManifest V").append(report.manifestVersion)
                append("\nCompatibility: ").append(report.level)
                report.reasons.take(5).forEach { append("\n• ").append(it) }
            }
            AlertDialog.Builder(activity)
                .setTitle("Install extension?")
                .setMessage(details)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Install") { _, _ ->
                    runtime.webExtensionController.install(Uri.fromFile(report.preparedFile).toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE)
                        .accept({ extension -> activity.runOnUiThread {
                            if (extension != null) {
                                runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, false)
                                Toast.makeText(activity, "${extension.metaData.name ?: report.name} installed", Toast.LENGTH_LONG).show()
                            }
                        } }, { error -> activity.runOnUiThread { Toast.makeText(activity, "Extension install failed: ${error?.message ?: "unsupported or unsigned package"}", Toast.LENGTH_LONG).show() } })
                }.show()
        } catch (error: Throwable) {
            Toast.makeText(activity, "Extension rejected: ${error.message ?: "invalid package"}", Toast.LENGTH_LONG).show()
        }
    }
}
