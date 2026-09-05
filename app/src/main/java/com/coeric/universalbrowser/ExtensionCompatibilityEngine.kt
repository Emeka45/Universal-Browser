package com.coeric.universalbrowser

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ExtensionCompatibilityEngine {
    enum class Level { FULL, HIGH, PARTIAL, UNSUPPORTED }

    data class Report(
        val name: String,
        val version: String,
        val manifestVersion: Int,
        val sourceFormat: String,
        val level: Level,
        val reasons: List<String>,
        val preparedFile: File,
        val transformed: Boolean
    )

    fun analyzeAndPrepare(context: Context, source: Uri): Report {
        val input = copyToCache(context, source, "extension-input")
        val zipFile = if (looksLikeZip(input)) input else extractCrxToZip(context, input)
        val manifest = readManifest(zipFile)
            ?: throw IllegalArgumentException("This package does not contain a valid manifest.json")
        val name = manifest.optString("name").ifBlank { "Unnamed extension" }
        val version = manifest.optString("version").ifBlank { "unknown" }
        val mv = manifest.optInt("manifest_version", 2)
        val reasons = mutableListOf<String>()
        var level = Level.FULL
        var transformed = false
        val permissions = jsonStringArray(manifest.optJSONArray("permissions"))
        val optionalPermissions = jsonStringArray(manifest.optJSONArray("optional_permissions"))
        val hardUnsupported = setOf("debugger", "declarativeContent", "sidePanel")
        val requestedHard = (permissions + optionalPermissions).filter { it in hardUnsupported }
        if (requestedHard.isNotEmpty()) {
            level = Level.UNSUPPORTED
            reasons += "Uses Chrome APIs with no Gecko equivalent: ${requestedHard.joinToString(", ")}."
        }
        if (manifest.has("side_panel")) {
            level = worse(level, Level.PARTIAL)
            reasons += "Chrome side panel is not portable to Gecko and needs an alternative UI."
        }
        if (manifest.has("externally_connectable")) {
            level = worse(level, Level.PARTIAL)
            reasons += "externally_connectable is not supported by Firefox/Gecko for web-page messaging."
        }
        if (manifest.has("devtools_page")) {
            level = worse(level, Level.PARTIAL)
            reasons += "DevTools pages require a browser-specific implementation."
        }
        val background = manifest.optJSONObject("background")
        if (mv >= 3 && background != null && background.has("service_worker") && !background.has("scripts")) {
            val worker = background.optString("service_worker").trim()
            if (worker.isNotEmpty()) {
                background.put("scripts", JSONArray().put(worker))
                manifest.put("background", background)
                transformed = true
                level = worse(level, Level.HIGH)
                reasons += "Added a Firefox/Gecko background-script fallback for the MV3 service worker."
            }
        }
        if (mv >= 3 && manifest.optJSONArray("web_accessible_resources") != null) {
            reasons += "Manifest V3 web-accessible resources were preserved without unsafe conversion."
        }
        if (level == Level.FULL) reasons += "Manifest uses WebExtension features that GeckoView can normally evaluate."
        if (source.toString().lowercase().endsWith(".crx")) reasons += "Chrome CRX container was unpacked for WebExtension inspection."
        reasons += "Final installation is still subject to GeckoView's Mozilla-signing requirement."
        val prepared = if (transformed || !looksLikeZip(input)) {
            val out = File(context.cacheDir, "universal-prepared-${System.currentTimeMillis()}.xpi")
            rewritePackage(zipFile, out, manifest)
            out
        } else zipFile
        return Report(name, version, mv, if (looksLikeZip(input)) "ZIP/XPI" else "CRX", level, reasons, prepared, transformed)
    }

    private fun worse(a: Level, b: Level): Level {
        val rank = mapOf(Level.FULL to 0, Level.HIGH to 1, Level.PARTIAL to 2, Level.UNSUPPORTED to 3)
        return if (rank.getValue(b) > rank.getValue(a)) b else a
    }

    private fun jsonStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private fun readManifest(zip: File): JSONObject? {
        ZipInputStream(FileInputStream(zip)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory && entry.name.equals("manifest.json", ignoreCase = true)) {
                    return JSONObject(String(zis.readBytes(), StandardCharsets.UTF_8))
                }
            }
        }
        return null
    }

    private fun rewritePackage(source: File, destination: File, manifest: JSONObject) {
        ZipInputStream(FileInputStream(source)).use { zis ->
            ZipOutputStream(FileOutputStream(destination)).use { zos ->
                var replaced = false
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.name.equals("manifest.json", ignoreCase = true)) {
                        zos.putNextEntry(ZipEntry("manifest.json"))
                        zos.write(manifest.toString(2).toByteArray(StandardCharsets.UTF_8))
                        zos.closeEntry()
                        replaced = true
                    } else {
                        val copy = ZipEntry(entry.name)
                        copy.time = entry.time
                        zos.putNextEntry(copy)
                        zis.copyTo(zos)
                        zos.closeEntry()
                    }
                }
                if (!replaced) throw IllegalArgumentException("manifest.json could not be rewritten")
            }
        }
    }

    private fun copyToCache(context: Context, uri: Uri, prefix: String): File {
        val file = File(context.cacheDir, "$prefix-${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Universal could not read the selected extension file")
        return file
    }

    private fun looksLikeZip(file: File): Boolean {
        FileInputStream(file).use { input ->
            val header = ByteArray(4)
            val count = input.read(header)
            return count == 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
        }
    }

    private fun extractCrxToZip(context: Context, crx: File): File {
        FileInputStream(crx).use { input ->
            val header = ByteArray(12)
            if (input.read(header) != 12 || String(header, 0, 4, StandardCharsets.US_ASCII) != "Cr24") {
                throw IllegalArgumentException("Unsupported extension container. Select a .crx, .xpi or ZIP WebExtension package.")
            }
            val version = littleEndianInt(header, 4)
            val zipOffset = when (version) {
                2 -> {
                    val publicKeyLength = littleEndianInt(header, 8)
                    val signatureLengthBytes = ByteArray(4)
                    if (input.read(signatureLengthBytes) != 4) throw IllegalArgumentException("Invalid CRX2 header")
                    16L + publicKeyLength.toLong() + littleEndianInt(signatureLengthBytes, 0).toLong()
                }
                3 -> {
                    val headerSize = littleEndianInt(header, 8)
                    12L + headerSize.toLong()
                }
                else -> throw IllegalArgumentException("Unsupported CRX version: $version")
            }
            FileInputStream(crx).use { full ->
                if (full.skip(zipOffset) != zipOffset) throw IllegalArgumentException("Invalid CRX package")
                val out = File(context.cacheDir, "universal-crx-${System.currentTimeMillis()}.zip")
                FileOutputStream(out).use { output -> full.copyTo(output) }
                return out
            }
        }
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }
}
