package com.coeric.universalbrowser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtensionController

object BrowserExtensionCenter {
    private const val PICK_EXTENSION = 7101

    fun openInstaller(activity: Activity) {
        activity.startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-xpinstall", "application/zip", "application/octet-stream"))
            }, PICK_EXTENSION
        )
    }

    fun handleResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?, runtime: GeckoRuntime): Boolean {
        if (requestCode != PICK_EXTENSION) return false
        if (resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true
        install(activity, uri, runtime)
        return true
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
            android.app.AlertDialog.Builder(activity)
                .setTitle("Install extension?")
                .setMessage(details)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Install") { _, _ ->
                    val controller = runtime.webExtensionController
                    val fileUri = Uri.fromFile(report.preparedFile).toString()
                    controller.install(fileUri, WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
                        { extension -> activity.runOnUiThread {
                            if (extension != null) {
                                controller.setAllowedInPrivateBrowsing(extension, false)
                                Toast.makeText(activity, "${extension.metaData.name ?: report.name} installed", Toast.LENGTH_LONG).show()
                            }
                        } },
                        { error -> activity.runOnUiThread { Toast.makeText(activity, "Extension install failed: ${error?.message ?: "unsupported or unsigned package"}", Toast.LENGTH_LONG).show() } }
                    )
                }.show()
        } catch (error: Throwable) {
            Toast.makeText(activity, "Extension rejected: ${error.message ?: "invalid package"}", Toast.LENGTH_LONG).show()
        }
    }
}
