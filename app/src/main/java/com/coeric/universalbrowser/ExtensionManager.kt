package com.coeric.universalbrowser

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile

/** Central extension installation surface for Universal Browser. */
object ExtensionManager {
    const val PICK_EXTENSION_REQUEST = 4817

    fun installFromUri(activity: Activity, uri: Uri) {
        val runtime = (activity.application as UniversalBrowserApp).getRuntime()
        val localFile = copyAndNormalizePackage(activity, uri)
        if (localFile == null) {
            toast(activity, "Unsupported extension package. Use XPI, CRX, or ZIP containing manifest.json.")
            return
        }
        installLocalFile(activity, runtime, localFile)
    }

    fun installLocalFile(context: Context, runtime: GeckoRuntime, file: File) {
        if (!file.exists()) {
            toast(context, "Extension file not found")
            return
        }
        val manifest = readManifest(file)
        if (manifest == null) {
            toast(context, "Invalid extension: manifest.json was not found")
            return
        }
        val name = manifest.optString("name").ifBlank { "Extension" }
        val version = manifest.optString("version").ifBlank { "unknown version" }
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
                val installedName = extension?.metaData?.name?.ifBlank { name } ?: name
                toast(context, "Installed: $installedName ($version)")
            },
            { error ->
                val reason = error?.message ?: "The package is invalid or not accepted by GeckoView"
                toast(context, "Could not install $name: $reason")
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

    /** Converts common downloaded CRX/ZIP packages into an XPI-compatible ZIP container. */
    private fun copyAndNormalizePackage(context: Context, uri: Uri): File? {
        return try {
            val extensionDir = File(context.filesDir, "extensions").apply { mkdirs() }
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val raw = File(extensionDir, "import-${System.currentTimeMillis()}.pkg")
            FileOutputStream(raw).use { output -> input.use { it.copyTo(output) } }
            val normalized = File(extensionDir, "import-${System.currentTimeMillis()}.xpi")
            FileInputStream(raw).use { source ->
                val header = ByteArray(16)
                val count = source.read(header)
                if (count >= 12 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x43, 0x72, 0x32, 0x34))) {
                    val version = littleEndianInt(header, 4)
                    if (version != 2) return null
                    val publicKeyLength = littleEndianInt(header, 8)
                    val signatureLength = littleEndianInt(header, 12)
                    source.skip((publicKeyLength + signatureLength).toLong())
                    FileOutputStream(normalized).use { source.copyTo(it) }
                } else if (count >= 12 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x43, 0x72, 0x32, 0x33))) {
                    val headerSize = littleEndianInt(header, 8)
                    source.skip(headerSize.toLong())
                    FileOutputStream(normalized).use { source.copyTo(it) }
                } else {
                    FileInputStream(raw).use { sourceAgain -> FileOutputStream(normalized).use { sourceAgain.copyTo(it) } }
                }
            }
            raw.delete()
            if (readManifest(normalized) == null) { normalized.delete(); return null }
            normalized
        } catch (_: Exception) {
            null
        }
    }

    private fun readManifest(file: File): JSONObject? {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.json") ?: return null
                zip.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
