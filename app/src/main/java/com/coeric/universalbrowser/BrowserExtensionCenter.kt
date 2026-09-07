package com.coeric.universalbrowser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/** Compatibility facade for the browser's extension UI and local GeckoView installation. */
object BrowserExtensionCenter {
    private const val PICK_EXTENSION_REQUEST = 4817

    fun openInstaller(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-xpinstall", "application/x-chrome-extension", "application/zip", "application/octet-stream"))
        }
        activity.startActivityForResult(intent, PICK_EXTENSION_REQUEST)
    }

    fun installFromUri(activity: Activity, uri: Uri) {
        val runtime = (activity.application as UniversalBrowserApp).getRuntime()
        val dir = File(activity.filesDir, "extensions").apply { mkdirs() }
        val file = File(dir, "extension-${System.currentTimeMillis()}.xpi")
        try {
            activity.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use { input.copyTo(it) } }
                ?: throw IllegalArgumentException("Unable to read extension file")
            val manifest = ZipFile(file).use { zip ->
                zip.getEntry("manifest.json")?.let { entry -> zip.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()) } }
            } ?: throw IllegalArgumentException("manifest.json not found")
            val name = manifest.optString("name").ifBlank { "Extension" }
            val controller = runtime.webExtensionController
            controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
                override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollectionPermissions: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse> =
                    GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, false, false))
            })
            controller.install(Uri.fromFile(file).toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
                { installed -> Toast.makeText(activity, "Installed: ${installed?.metaData?.name ?: name}", Toast.LENGTH_LONG).show() },
                { error -> Toast.makeText(activity, "Could not install $name: ${error?.message ?: "package rejected"}", Toast.LENGTH_LONG).show() }
            )
        } catch (error: Throwable) {
            file.delete()
            Toast.makeText(activity, "Invalid extension: ${error.message ?: "unsupported package"}", Toast.LENGTH_LONG).show()
        }
    }
}
