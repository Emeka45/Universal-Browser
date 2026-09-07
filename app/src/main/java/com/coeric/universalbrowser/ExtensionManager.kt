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

/** First-class extension installation and management surface. */
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
        if (!file.exists()) { toast(context, "Extension file not found"); return }
        val manifest = readManifest(file)
        if (manifest == null) { toast(context, "Invalid extension: manifest.json was not found"); return }
        val name = manifest.optString("name").ifBlank { "Extension" }
        val version = manifest.optString("version").ifBlank { "unknown version" }
        val controller = runtime.webExtensionController
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollectionPermissions: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse> =
                GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, false, false))
        })
        controller.install(Uri.fromFile(file).toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
            { extension -> toast(context, "Installed: ${extension?.metaData?.name?.ifBlank { name } ?: name} ($version)") },
            { error -> toast(context, "Could not install $name: ${error?.message ?: "package rejected by GeckoView"}") }
        )
    }

    fun showInstalled(context: Context) {
        val runtime = (context.applicationContext as UniversalBrowserApp).getRuntime()
        runtime.webExtensionController.list().accept(
            { extensions ->
                val names = extensions.orEmpty().map { "${it.metaData.name} (${it.metaData.version})" }
                android.app.AlertDialog.Builder(context).setTitle("Installed extensions")
                    .setMessage(if (names.isEmpty()) "No extensions installed yet." else names.joinToString("\n"))
                    .setPositiveButton("Close", null).show()
            },
            { toast(context, "Could not load installed extensions") }
        )
    }

    private fun copyAndNormalizePackage(context: Context, uri: Uri): File? {
        return try {
            val dir = File(context.filesDir, "extensions").apply { mkdirs() }
            val raw = File(dir, "import-${System.currentTimeMillis()}.pkg")
            context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(raw).use { input.copyTo(it) } } ?: return null
            val normalized = File(dir, "import-${System.currentTimeMillis()}.xpi")
            FileInputStream(raw).use { source ->
                val header = ByteArray(16)
                val count = source.read(header)
                val isCrx = count >= 8 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x43, 0x72, 0x32, 0x34))
                if (isCrx) {
                    val version = littleEndianInt(header, 4)
                    when (version) {
                        2 -> {
                            if (count < 16) return null
                            val publicKeyLength = littleEndianInt(header, 8)
                            val signatureLength = littleEndianInt(header, 12)
                            source.skip((publicKeyLength + signatureLength).toLong())
                            FileOutputStream(normalized).use { source.copyTo(it) }
                        }
                        3 -> {
                            if (count < 12) return null
                            val headerSize = littleEndianInt(header, 8)
                            if (headerSize < 0 || headerSize > 64 * 1024 * 1024) return null
                            FileInputStream(raw).use { full ->
                                full.skip(12)
                                full.skip(headerSize.toLong())
                                FileOutputStream(normalized).use { full.copyTo(it) }
                            }
                        }
                        else -> return null
                    }
                } else {
                    FileInputStream(raw).use { again -> FileOutputStream(normalized).use { again.copyTo(it) } }
                }
            }
            raw.delete()
            if (readManifest(normalized) == null) { normalized.delete(); return null }
            normalized
        } catch (_: Exception) { null }
    }

    private fun readManifest(file: File): JSONObject? = try {
        ZipFile(file).use { zip -> zip.getEntry("manifest.json")?.let { entry -> zip.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()) } } }
    } catch (_: Exception) { null }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
