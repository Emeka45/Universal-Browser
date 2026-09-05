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

/**
 * Inspects a WebExtension package before GeckoView sees it.
 *
 * Important: this is a compatibility analyser, not a fake Chrome-runtime shim.
 * GeckoView executes Firefox/WebExtension APIs and still enforces Mozilla's
 * signing policy for normal installed XPI packages. Chrome APIs that do not
 * have a Gecko equivalent are therefore reported instead of being silently
 * rewritten into broken behaviour.
 */
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
        val sourceWasZip = looksLikeZip(input)
        val zipFile = if (sourceWasZip) input else extractCrxToZip(context, input)
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
        val allPermissions = permissions + optionalPermissions

        // These are intentionally hard failures. Pretending that these APIs
        // work would be worse than refusing the package before installation.
        val hardUnsupported = setOf(
            "debugger",
            "declarativeContent",
            "sidePanel"
        )
        val requestedHard = allPermissions.filter { it in hardUnsupported }.distinct()
        if (requestedHard.isNotEmpty()) {
            level = Level.UNSUPPORTED
            reasons += "Uses Chrome APIs with no safe Gecko equivalent: ${requestedHard.joinToString(", ")}."
        }

        if (manifest.has("side_panel") || manifest.has("sidePanel")) {
            level = worse(level, Level.PARTIAL)
            reasons += "Chrome side panel is not portable to Gecko; the extension needs an alternative UI."
        }
        if (manifest.has("externally_connectable")) {
            level = worse(level, Level.PARTIAL)
            reasons += "externally_connectable is not a portable Firefox/Gecko page-messaging feature."
        }
        if (manifest.has("devtools_page") || manifest.has("devtools_panel")) {
            level = worse(level, Level.PARTIAL)
            reasons += "DevTools integration is browser-specific and may require a Gecko-specific implementation."
        }
        if (manifest.has("chrome_settings_overrides")) {
            level = worse(level, Level.PARTIAL)
            reasons += "chrome_settings_overrides is Chrome-specific and will not be applied by Gecko."
        }
        if (manifest.has("chrome_url_overrides")) {
            level = worse(level, Level.PARTIAL)
            reasons += "chrome_url_overrides is Chrome-specific; Firefox uses different browser-page mechanisms."
        }

        val background = manifest.optJSONObject("background")
        if (mv >= 3 && background?.has("service_worker") == true && !background.has("scripts")) {
            // Do NOT convert a Chrome MV3 service worker into a Firefox
            // background script. The execution model is different and such a
            // rewrite would make the package look installable while silently
            // breaking its event lifecycle.
            level = worse(level, Level.PARTIAL)
            reasons += "MV3 service_worker detected. Gecko compatibility requires a real Firefox background-script conversion; no unsafe automatic rewrite was applied."
        }

        if (mv >= 3 && manifest.optJSONArray("web_accessible_resources") != null) {
            reasons += "Manifest V3 web-accessible resources were left untouched; Firefox compatibility must be validated by Gecko."
        }

        if (manifest.has("minimum_chrome_version")) {
            level = worse(level, Level.PARTIAL)
            reasons += "minimum_chrome_version is informational here; Gecko will validate actual runtime/API compatibility."
        }

        val browserSpecific = manifest.optJSONObject("browser_specific_settings")
        if (browserSpecific != null) {
            val gecko = browserSpecific.optJSONObject("gecko")
            if (gecko?.has("id") == true) {
                reasons += "Firefox browser_specific_settings.gecko.id is present."
            }
        }

        if (level == Level.FULL) {
            level = if (mv >= 3) Level.HIGH else Level.FULL
            reasons += if (mv >= 3) {
                "No known hard-blocking Chrome-only API was detected; Gecko must still validate the MV3 package."
            } else {
                "Manifest uses WebExtension features that GeckoView can normally evaluate."
            }
        }

        if (!sourceWasZip) {
            reasons += "Chrome CRX container was unpacked for WebExtension inspection."
        }
        reasons += "Normal GeckoView package installation still requires Mozilla signing."

        // Only rewrite the package when the container itself had to be
        // converted from CRX to ZIP/XPI. We never rewrite manifest semantics.
        val prepared = if (!sourceWasZip) {
            val out = File(context.cacheDir, "universal-prepared-${System.currentTimeMillis()}.xpi")
            copyZip(zipFile, out)
            out
        } else {
            zipFile
        }

        return Report(
            name = name,
            version = version,
            manifestVersion = mv,
            sourceFormat = if (sourceWasZip) "ZIP/XPI" else "CRX",
            level = level,
            reasons = reasons,
            preparedFile = prepared,
            transformed = transformed
        )
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

    private fun copyZip(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
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
