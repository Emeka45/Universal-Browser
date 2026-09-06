package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import org.mozilla.geckoview.GeckoSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Production browser actions backed by GeckoView instead of JavaScript shims. */
object BrowserFeatureCenter {
    fun savePageAsPdf(activity: Activity, session: GeckoSession) {
        session.saveAsPdf().accept({ input ->
            if (input == null) {
                activity.runOnUiThread { toast(activity, "Could not create PDF") }
                return@accept
            }
            Thread {
                try {
                    val name = "Universal-${timestamp()}.pdf"
                    val uri = insertPublicFile(activity, name, "application/pdf", Environment.DIRECTORY_DOWNLOADS)
                        ?: throw IllegalStateException("Android storage is unavailable")
                    activity.contentResolver.openOutputStream(uri)?.use { output -> input.use { source -> source.copyTo(output) } }
                        ?: throw IllegalStateException("Could not open output file")
                    activity.runOnUiThread { toast(activity, "PDF saved to Downloads/$name") }
                } catch (error: Throwable) {
                    activity.runOnUiThread { toast(activity, "PDF save failed: ${error.message ?: "unknown error"}") }
                }
            }.start()
        }, { error -> activity.runOnUiThread { toast(activity, "PDF save failed: ${error.message ?: "unknown error"}") } })
    }

    fun printPage(activity: Activity, session: GeckoSession) {
        try {
            session.printPageContent()
            toast(activity, "Print request sent")
        } catch (error: Throwable) {
            toast(activity, "Printing unavailable: ${error.message ?: "unsupported page"}")
        }
    }

    fun captureVisiblePage(activity: Activity, session: GeckoSession) {
        try {
            val display = session.acquireDisplay()
            display.capturePixels().accept({ bitmap ->
                try {
                    if (bitmap == null) {
                        activity.runOnUiThread { toast(activity, "Screenshot unavailable") }
                        return@accept
                    }
                    Thread {
                        try {
                            val name = "Universal-${timestamp()}.png"
                            val uri = insertPublicFile(activity, name, "image/png", Environment.DIRECTORY_PICTURES, "Universal Browser")
                                ?: throw IllegalStateException("Android storage is unavailable")
                            activity.contentResolver.openOutputStream(uri)?.use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
                                ?: throw IllegalStateException("Could not open output file")
                            activity.runOnUiThread { toast(activity, "Screenshot saved to Pictures/Universal Browser") }
                        } catch (error: Throwable) {
                            activity.runOnUiThread { toast(activity, "Screenshot failed: ${error.message ?: "unknown error"}") }
                        }
                    }.start()
                } finally {
                    try { session.releaseDisplay(display) } catch (_: Throwable) { }
                }
            }, { error ->
                try { session.releaseDisplay(display) } catch (_: Throwable) { }
                activity.runOnUiThread { toast(activity, "Screenshot failed: ${error.message ?: "unknown error"}") }
            })
        } catch (error: Throwable) {
            toast(activity, "Screenshot unavailable: ${error.message ?: "page is not ready"}")
        }
    }

    fun showSiteControls(activity: Activity, session: GeckoSession, url: String) {
        val host = try { Uri.parse(url).host ?: url } catch (_: Throwable) { url }
        val current = session.settings
        val items = arrayOf(
            "JavaScript: ${if (current.allowJavascript) "On" else "Off"}",
            "Tracking protection: ${if (current.useTrackingProtection) "On" else "Off"}",
            "Private browsing: ${if (current.usePrivateMode) "On" else "Off"}"
        )
        AlertDialog.Builder(activity).setTitle("Site controls\n$host")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> current.allowJavascript = !current.allowJavascript
                    1 -> current.useTrackingProtection = !current.useTrackingProtection
                    2 -> toast(activity, "Private mode is controlled when the session is created")
                }
                toast(activity, "Site setting updated")
            }.setNegativeButton("Close", null).show()
    }

    private fun insertPublicFile(activity: Activity, name: String, mime: String, relativePath: String, subFolder: String? = null): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val relative = if (subFolder.isNullOrBlank()) relativePath else "$relativePath/$subFolder"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
        }
        val collection = if (relativePath == Environment.DIRECTORY_PICTURES) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return activity.contentResolver.insert(collection, values)
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    private fun toast(context: Activity, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
