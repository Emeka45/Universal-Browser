package com.coeric.universalbrowser

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.io.FileOutputStream

/** Central extension installation surface for Universal Browser. */
object ExtensionManager {
    const val PICK_EXTENSION_REQUEST = 4817

    fun installFromUri(activity: Activity, uri: Uri) {
        val runtime = (activity.application as UniversalBrowserApp).getRuntime()
        val localFile = copyToPrivateStorage(activity, uri)
        if (localFile == null) {
            toast(activity, "Could not read the extension package")
            return
        }
        installLocalFile(activity, runtime, localFile)
    }

    fun installLocalFile(context: Context, runtime: GeckoRuntime, file: File) {
        if (!file.exists()) {
            toast(context, "Extension file not found")
            return
        }
        val controller = runtime.webExtensionController
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> =
                GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, false, false))
        })
        controller.install(
            Uri.fromFile(file).toString(),
            WebExtensionController.INSTALLATION_METHOD_FROM_FILE
        ).accept(
            { extension ->
                val name = extension?.metaData?.name?.ifBlank { "Extension" } ?: "Extension"
                toast(context, "Installed: $name")
            },
            { error ->
                val reason = error?.message ?: "Unsupported, unsigned, or invalid extension package"
                toast(context, "Extension install failed: $reason")
            }
        )
    }

    fun showInstalled(context: Context) {
        val runtime = (context.applicationContext as UniversalBrowserApp).getRuntime()
        runtime.webExtensionController.list().accept(
            { extensions ->
                val names = extensions.orEmpty().map { "${it.metaData.name} (${it.metaData.version})" }
                val message = if (names.isEmpty()) "No extensions installed yet." else names.joinToString("\n")
                android.app.AlertDialog.Builder(context)
                    .setTitle("Installed extensions")
                    .setMessage(message)
                    .setPositiveButton("Close", null)
                    .show()
            },
            { toast(context, "Could not load installed extensions") }
        )
    }

    private fun copyToPrivateStorage(context: Context, uri: Uri): File? {
        return try {
            val extensionDir = File(context.filesDir, "extensions").apply { mkdirs() }
            val source = uri.toString().lowercase()
            val suffix = when {
                source.endsWith(".xpi") -> ".xpi"
                source.endsWith(".crx") -> ".crx"
                else -> ".zip"
            }
            val file = File(extensionDir, "import-${System.currentTimeMillis()}$suffix")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: return null
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
