package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.TranslationsController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Advanced browser tools implemented against GeckoView and Android primitives. */
object BrowserAdvancedTools {
    fun openReaderMode(activity: Activity, session: GeckoSession, url: String) {
        if (url.isBlank() || !url.startsWith("http", true)) {
            toast(activity, "Open a webpage first")
            return
        }
        session.loadUri("about:reader?url=${Uri.encode(url)}")
    }

    fun translatePage(activity: Activity, session: GeckoSession) {
        val translation = session.sessionTranslation
        if (translation == null) {
            toast(activity, "Translation engine is unavailable on this GeckoView build")
            return
        }
        val languages = arrayOf(
            "English" to "en", "French" to "fr", "Spanish" to "es",
            "German" to "de", "Portuguese" to "pt", "Arabic" to "ar"
        )
        AlertDialog.Builder(activity)
            .setTitle("Translate page to")
            .setItems(languages.map { it.first }.toTypedArray()) { _, which ->
                val options = TranslationsController.SessionTranslation.TranslationOptions.Builder()
                    .downloadModel(true).build()
                translation.translate("", languages[which].second, options).accept(
                    { toast(activity, "Translation started") },
                    { error -> toast(activity, "Translation failed: ${error?.message ?: "unavailable"}") }
                )
            }
            .setNeutralButton("Restore original") { _, _ ->
                translation.restoreOriginalPage().accept(
                    { toast(activity, "Original page restored") },
                    { error -> toast(activity, "Could not restore page: ${error?.message ?: "unknown error"}") }
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /** Saves an actual standalone offline reader snapshot using Gecko's page extractor. */
    fun saveOfflineSnapshot(activity: Activity, session: GeckoSession) {
        val extractor = session.getSessionPageExtractor()
        extractor.getPageContent().accept(
            { content ->
                val text = content?.toString()?.trim().orEmpty()
                if (text.isBlank()) {
                    toast(activity, "This page could not be extracted for offline reading")
                    return@accept
                }
                val title = try { Uri.parse(session.currentUri?.toString().orEmpty()).host ?: "Offline page" } catch (_: Throwable) { "Offline page" }
                val url = session.currentUri?.toString().orEmpty()
                try {
                    val file = OfflinePageStore.save(activity, title, url, text)
                    toast(activity, "Offline page saved: ${file.name}")
                } catch (error: Throwable) {
                    toast(activity, "Offline save failed: ${error.message ?: "unknown error"}")
                }
            },
            { error -> toast(activity, "Offline extraction failed: ${error?.message ?: "unknown error"}") }
        )
    }

    fun showQrScanner(activity: Activity) {
        activity.startActivity(Intent(activity, QrScannerActivity::class.java))
    }

    fun showQrGenerator(activity: Activity, value: String) {
        if (value.isBlank()) {
            toast(activity, "Open a page first")
            return
        }
        try {
            val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 720, 720)
            val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
            for (x in 0 until 720) for (y in 0 until 720) {
                bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
            val image = ImageView(activity).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                setPadding(24, 24, 24, 24)
            }
            AlertDialog.Builder(activity)
                .setTitle("Universal QR")
                .setView(image)
                .setPositiveButton("Save") { _, _ -> saveQr(activity, bitmap) }
                .setNegativeButton("Close", null)
                .show()
        } catch (error: Throwable) {
            toast(activity, "QR generation failed: ${error.message ?: "unknown error"}")
        }
    }

    private fun saveQr(activity: Activity, bitmap: Bitmap) {
        val name = "Universal-QR-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Universal Browser")
                }
                val uri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
                activity.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    ?: throw IllegalStateException("Could not open image output")
            } else {
                val dir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: activity.filesDir
                val file = File(dir, name)
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            toast(activity, "QR saved as $name")
        } catch (error: Throwable) {
            toast(activity, "QR save failed: ${error.message ?: "unknown error"}")
        }
    }

    private fun toast(activity: Activity, message: String) =
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}
