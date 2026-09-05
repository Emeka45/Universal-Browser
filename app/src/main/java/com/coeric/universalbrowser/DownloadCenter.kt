package com.coeric.universalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/** Browser download center backed by Android DownloadManager. */
object DownloadCenter {
    private data class Item(val id: Long, val title: String, val status: Int, val downloaded: Long, val total: Long, val localUri: String?, val date: Long)

    fun show(activity: Activity) {
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val items = query(manager)
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 4, 18, 8) }
        if (items.isEmpty()) {
            root.addView(TextView(activity).apply { text = "No downloads yet.\n\nFiles downloaded by Universal will appear here with their status."; textSize = 14f; gravity = Gravity.CENTER; setPadding(12, 30, 12, 30) })
        } else {
            items.forEach { item -> root.addView(row(activity, manager, item), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }) }
        }
        AlertDialog.Builder(activity).setTitle("Downloads").setView(root)
            .setNeutralButton("Refresh") { _, _ -> show(activity) }
            .setPositiveButton("Done", null).show()
    }

    private fun row(activity: Activity, manager: DownloadManager, item: Item): LinearLayout {
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12); setBackgroundColor(0xFFF7F6FB.toInt()) }
        box.addView(TextView(activity).apply { text = item.title.ifBlank { "Universal download" }; textSize = 14.5f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        box.addView(TextView(activity).apply { text = statusText(item); textSize = 12f; setPadding(0, 4, 0, 8) })
        val actions = LinearLayout(activity).apply { gravity = Gravity.END }
        if (item.status == DownloadManager.STATUS_SUCCESSFUL && !item.localUri.isNullOrBlank()) {
            actions.addView(action(activity, "Open") {
                try { activity.startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(item.localUri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
                catch (_: Throwable) { Toast.makeText(activity, "No app can open this file.", Toast.LENGTH_SHORT).show() }
            })
        }
        actions.addView(action(activity, if (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING) "Cancel" else "Remove") {
            manager.remove(item.id); show(activity)
        })
        box.addView(actions)
        return box
    }

    private fun action(activity: Activity, label: String, callback: () -> Unit) = TextView(activity).apply {
        text = label; textSize = 12f; gravity = Gravity.CENTER; setPadding(18, 8, 18, 8); setTextColor(0xFF6548FF.toInt()); setOnClickListener { callback() }
    }

    private fun query(manager: DownloadManager): List<Item> {
        val all = manager.query(DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or
                DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED
        ))
        all.use { c ->
            val result = mutableListOf<Item>()
            val id = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val title = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val status = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val current = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val total = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val local = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val date = c.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
            while (c.moveToNext()) result += Item(c.getLong(id), if (title >= 0) c.getString(title).orEmpty() else "Universal download", c.getInt(status), c.getLong(current), c.getLong(total), if (local >= 0) c.getString(local) else null, if (date >= 0) c.getLong(date) else 0L)
            return result.sortedByDescending { it.date }
        }
    }

    private fun statusText(item: Item): String {
        val state = when (item.status) {
            DownloadManager.STATUS_PENDING -> "Waiting"
            DownloadManager.STATUS_RUNNING -> "Downloading"
            DownloadManager.STATUS_PAUSED -> "Paused"
            DownloadManager.STATUS_SUCCESSFUL -> "Completed"
            DownloadManager.STATUS_FAILED -> "Failed"
            else -> "Unknown"
        }
        val size = if (item.total > 0) " • ${item.downloaded}/${item.total} bytes" else ""
        val time = if (item.date > 0) " • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.date))}" else ""
        return state + size + time
    }
}
