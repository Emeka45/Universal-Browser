package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Browser download center backed by Android DownloadManager. */
object DownloadCenter {
    private data class Item(
        val id: Long, val title: String, val url: String?, val status: Int, val reason: Int,
        val downloaded: Long, val total: Long, val localUri: String?, val mediaType: String?, val date: Long
    )

    fun show(activity: Activity) {
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val items = query(manager)
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 4, 18, 8) }
        if (items.isEmpty()) {
            root.addView(TextView(activity).apply { text = "No downloads yet.\n\nFiles downloaded by Universal will appear here with their status."; textSize = 14f; gravity = Gravity.CENTER; setPadding(12, 30, 12, 30) })
        } else {
            items.forEach { item -> root.addView(row(activity, manager, item), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }) }
        }
        AlertDialog.Builder(activity).setTitle("Downloads (${items.size})").setView(root)
            .setNeutralButton("Refresh") { _, _ -> show(activity) }.setPositiveButton("Done", null).show()
    }

    private fun row(activity: Activity, manager: DownloadManager, item: Item): LinearLayout {
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12); setBackgroundColor(0xFFF7F6FB.toInt()) }
        box.addView(TextView(activity).apply { text = item.title.ifBlank { "Universal download" }; textSize = 14.5f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        box.addView(TextView(activity).apply { text = statusText(item); textSize = 12f; setPadding(0, 4, 0, 8) })
        if (isActive(item.status) && item.total > 0L) {
            val percent = progressPercent(item)
            box.addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = percent })
            box.addView(TextView(activity).apply { text = "$percent%"; textSize = 11f; gravity = Gravity.END; setPadding(0, 2, 0, 4) })
        }
        val actions = LinearLayout(activity).apply { gravity = Gravity.END }
        if (item.status == DownloadManager.STATUS_SUCCESSFUL && !item.localUri.isNullOrBlank()) {
            actions.addView(action(activity, "Open") {
                try { activity.startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(item.localUri); item.mediaType?.takeIf { it.isNotBlank() }?.let { type = it }; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
                catch (_: Throwable) { Toast.makeText(activity, "No app can open this file.", Toast.LENGTH_SHORT).show() }
            })
        }
        if (item.status == DownloadManager.STATUS_PAUSED) {
            actions.addView(action(activity, "Resume") { retry(activity, manager, item, true) })
            actions.addView(action(activity, "Cancel") { manager.remove(item.id); show(activity) })
        } else if (item.status == DownloadManager.STATUS_FAILED) {
            actions.addView(action(activity, "Retry") { retry(activity, manager, item, false) })
            actions.addView(action(activity, "Details") { AlertDialog.Builder(activity).setTitle("Download failed").setMessage(downloadReason(item.reason)).setPositiveButton("OK", null).show() })
        } else if (isActive(item.status)) {
            actions.addView(action(activity, "Cancel") { manager.remove(item.id); show(activity) })
        } else {
            actions.addView(action(activity, "Remove") { manager.remove(item.id); show(activity) })
        }
        box.addView(actions)
        return box
    }

    private fun retry(activity: Activity, manager: DownloadManager, item: Item, resumed: Boolean) {
        val url = item.url
        if (url.isNullOrBlank()) { Toast.makeText(activity, "Original URL is unavailable; cannot resume.", Toast.LENGTH_SHORT).show(); return }
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(item.title.ifBlank { "Universal download" })
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, item.title.ifBlank { "Universal-download" })
                item.mediaType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
            }
            manager.enqueue(request); manager.remove(item.id)
            Toast.makeText(activity, if (resumed) "Download resumed from the beginning" else "Download retry queued", Toast.LENGTH_SHORT).show()
            show(activity)
        } catch (error: Throwable) { Toast.makeText(activity, "Download action failed: ${error.message ?: "unknown error"}", Toast.LENGTH_SHORT).show() }
    }

    private fun action(activity: Activity, label: String, callback: () -> Unit) = TextView(activity).apply { text = label; textSize = 12f; gravity = Gravity.CENTER; setPadding(18, 8, 18, 8); setTextColor(0xFF6548FF.toInt()); setOnClickListener { callback() } }

    private fun query(manager: DownloadManager): List<Item> {
        val all = manager.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED))
        all.use { c ->
            val result = mutableListOf<Item>()
            val id = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID); val title = c.getColumnIndex(DownloadManager.COLUMN_TITLE); val url = c.getColumnIndex(DownloadManager.COLUMN_URI)
            val status = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS); val reason = c.getColumnIndex(DownloadManager.COLUMN_REASON); val current = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR); val total = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val local = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI); val mediaType = c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE); val date = c.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
            while (c.moveToNext()) result += Item(c.getLong(id), if (title >= 0) c.getString(title).orEmpty() else "Universal download", if (url >= 0) c.getString(url) else null, c.getInt(status), if (reason >= 0) c.getInt(reason) else 0, c.getLong(current), c.getLong(total), if (local >= 0) c.getString(local) else null, if (mediaType >= 0) c.getString(mediaType) else null, if (date >= 0) c.getLong(date) else 0L)
            return result.sortedByDescending { it.date }
        }
    }

    private fun isActive(status: Int) = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
    private fun progressPercent(item: Item) = if (item.total > 0L) ((item.downloaded.toDouble() / item.total) * 100.0).toInt().coerceIn(0, 100) else 0
    private fun statusText(item: Item): String {
        val state = when (item.status) { DownloadManager.STATUS_PENDING -> "Waiting"; DownloadManager.STATUS_RUNNING -> "Downloading"; DownloadManager.STATUS_PAUSED -> "Paused by Android • Resume available"; DownloadManager.STATUS_SUCCESSFUL -> "Completed"; DownloadManager.STATUS_FAILED -> "Failed"; else -> "Unknown" }
        val size = if (item.total > 0) " • ${formatBytes(item.downloaded)} / ${formatBytes(item.total)}" else if (item.downloaded > 0) " • ${formatBytes(item.downloaded)}" else ""
        val time = if (item.date > 0) " • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.date))}" else ""
        return state + size + time
    }
    private fun formatBytes(bytes: Long): String { if (bytes < 1024L) return "$bytes B"; val units = arrayOf("KB", "MB", "GB", "TB"); var value = bytes.toDouble(); var index = -1; while (value >= 1024.0 && index < units.lastIndex) { value /= 1024.0; index++ }; return String.format(Locale.US, "%.1f %s", value, units[index]) }
    private fun downloadReason(reason: Int) = when (reason) { DownloadManager.ERROR_FILE_ERROR -> "The destination file could not be created."; DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The server returned an unsupported HTTP response."; DownloadManager.ERROR_HTTP_DATA_ERROR -> "The server returned invalid or incomplete data."; DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "The download followed too many redirects."; DownloadManager.ERROR_INSUFFICIENT_SPACE -> "There is not enough storage space."; DownloadManager.ERROR_DEVICE_NOT_FOUND -> "The download storage device is unavailable."; DownloadManager.ERROR_CANNOT_RESUME -> "The download could not be resumed."; DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "A file with this name already exists."; else -> "Android Download Manager could not complete the download (code $reason)." }
}
