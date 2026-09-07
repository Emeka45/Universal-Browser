package com.coeric.universalbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** User-initiated, resumable direct-file downloader using headers captured from Gecko. */
class DownloadService : Service() {
    companion object {
        const val ACTION_START = "com.coeric.universalbrowser.download.START"
        const val ACTION_PAUSE = "com.coeric.universalbrowser.download.PAUSE"
        const val ACTION_RESUME = "com.coeric.universalbrowser.download.RESUME"
        const val ACTION_CANCEL = "com.coeric.universalbrowser.download.CANCEL"
        const val EXTRA_ID = "download_id"
        private const val CHANNEL = "universal_downloads"
        private const val NOTIFICATION_ID = 4701

        fun start(context: Context, id: String, url: String, title: String, referer: String, cookies: String = "") {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, id); putExtra("url", url); putExtra("title", title)
                putExtra("referer", referer); putExtra("cookies", cookies)
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun control(context: Context, action: String, id: String) {
            val intent = Intent(context, DownloadService::class.java).apply { this.action = action; putExtra(EXTRA_ID, id) }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    private var worker: Thread? = null
    @Volatile private var stopRequested = false

    override fun onCreate() {
        super.onCreate(); createChannel()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification("Preparing download", -1, null, null), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, notification("Preparing download", -1, null, null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> startTask(intent.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY)
            ACTION_PAUSE -> pauseTask(intent.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY)
            ACTION_CANCEL -> cancelTask(intent.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY)
            null -> resumePersistedTask()
        }
        return START_STICKY
    }

    private fun startTask(id: String) {
        if (worker?.isAlive == true) return
        val store = DownloadTaskStore(this)
        val task = store.all().firstOrNull { it.id == id } ?: run { startNextQueued(); return }
        if (task.state == "cancelled" || task.state == "completed") { startNextQueued(); return }
        stopRequested = false
        worker = Thread { download(task) }.also { it.start() }
    }

    private fun startNextQueued() {
        if (worker?.isAlive == true) return
        DownloadTaskStore(this).all().firstOrNull { it.state == "queued" }?.let { startTask(it.id) } ?: stopSelf()
    }

    private fun resumePersistedTask() {
        val task = DownloadTaskStore(this).all().firstOrNull { it.state == "downloading" || it.state == "queued" || it.state == "paused" }
        if (task == null) stopSelf() else startTask(task.id)
    }

    private fun pauseTask(id: String) {
        val store = DownloadTaskStore(this); val task = store.all().firstOrNull { it.id == id } ?: run { stopSelf(); return }
        if (task.state == "downloading" || task.state == "queued") store.updateState(id, "paused")
        stopRequested = true; worker?.interrupt()
        if (worker?.isAlive != true) { updateNotification(task.title, task.bytes, task.total, id, true); startNextQueued() }
    }

    private fun cancelTask(id: String) {
        val store = DownloadTaskStore(this); val task = store.all().firstOrNull { it.id == id }
        task?.tempPath?.takeIf { it.isNotBlank() }?.let { File(it).delete() }
        if (task != null) store.update(id, state = "cancelled", bytes = 0L, tempPath = "", clearTempPath = true)
        stopRequested = true; worker?.interrupt()
        if (worker?.isAlive != true) startNextQueued() else stopSelf()
    }

    private fun download(task: DownloadTaskStore.Task) {
        val store = DownloadTaskStore(this)
        val temp = task.tempPath?.takeIf { it.isNotBlank() }?.let(::File) ?: File(cacheDir, "downloads/${task.id}.part").also { it.parentFile?.mkdirs() }
        store.update(task.id, state = "downloading", tempPath = temp.absolutePath)
        var connection: HttpURLConnection? = null
        try {
            var existing = if (temp.exists()) temp.length() else 0L
            connection = open(task.url, task.referer, task.cookies, existing)
            val response = connection.responseCode
            val append = existing > 0L && response == HttpURLConnection.HTTP_PARTIAL
            if (!append) existing = 0L
            val contentLength = connection.contentLengthLong
            val total = if (contentLength > 0L) existing + contentLength else -1L
            store.update(task.id, bytes = existing, total = total)
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(temp, append).use { output ->
                    val buffer = ByteArray(32 * 1024); var bytes = existing; var lastUpdate = 0L
                    while (!stopRequested) {
                        val count = input.read(buffer); if (count < 0) break
                        output.write(buffer, 0, count); bytes += count
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 500L) { store.updateBytes(task.id, bytes, total); updateNotification(task.title, bytes, total, task.id, false); lastUpdate = now }
                    }
                    output.flush()
                    if (stopRequested) {
                        if (store.all().firstOrNull { it.id == task.id }?.state != "cancelled") store.update(task.id, state = "paused", bytes = bytes, total = total, tempPath = temp.absolutePath)
                        return
                    }
                    store.updateBytes(task.id, bytes, if (total > 0L) total else bytes)
                }
            }
            publishFile(temp, task)
            store.update(task.id, state = "completed", bytes = temp.length(), total = temp.length(), localUri = publishedUri.toString(), mimeType = guessMime(task.url), clearTempPath = true)
            updateNotification(task.title, temp.length(), temp.length(), task.id, true); temp.delete()
        } catch (t: Throwable) {
            val current = store.all().firstOrNull { it.id == task.id }
            if (current?.state != "paused" && current?.state != "cancelled") store.update(task.id, state = "failed")
            updateNotification(task.title, current?.bytes ?: 0L, current?.total ?: -1L, task.id, true)
            // Never leave a partial file behind after a terminal failure; paused downloads retain it.
            if (current?.state != "paused" && current?.state != "cancelled") temp.delete()
        } finally {
            connection?.disconnect(); worker = null
            val next = store.all().firstOrNull { it.state == "queued" }
            if (next != null) startTask(next.id) else if (store.all().none { it.state == "downloading" }) stopSelf()
        }
    }

    private var publishedUri: Uri = Uri.EMPTY

    private fun publishFile(temp: File, task: DownloadTaskStore.Task) {
        val name = task.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "Universal-download" }
        val displayName = if (name.contains('.')) name else name + extensionForMime(guessMime(task.url))
        if (Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply { put(android.provider.MediaStore.Downloads.DISPLAY_NAME, displayName); put(android.provider.MediaStore.Downloads.MIME_TYPE, guessMime(task.url)); put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Universal Browser"); put(android.provider.MediaStore.Downloads.IS_PENDING, 1) }
            val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create Downloads entry")
            try {
                contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out, 64 * 1024) } } ?: error("Unable to open Downloads entry")
                contentResolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Downloads.IS_PENDING, 0) }, null, null); publishedUri = uri
            } catch (t: Throwable) {
                contentResolver.delete(uri, null, null)
                throw t
            }
        } else {
            val target = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), displayName); target.parentFile?.mkdirs(); temp.copyTo(target, overwrite = true); publishedUri = Uri.fromFile(target)
        }
    }

    private fun open(url: String, referer: String, cookies: String, existing: Long): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 60000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) UniversalBrowser/1.0")
        if (cookies.isNotBlank()) c.setRequestProperty("Cookie", cookies)
        if (referer.isNotBlank()) c.setRequestProperty("Referer", referer)
        if (existing > 0L) c.setRequestProperty("Range", "bytes=$existing-")
        if (c.responseCode !in 200..299 && c.responseCode != HttpURLConnection.HTTP_PARTIAL) throw IllegalArgumentException("Download server returned HTTP ${c.responseCode}")
        return c
    }

    private fun guessMime(url: String): String = when (url.substringBefore('?').substringAfterLast('.').lowercase()) {
        "mp4", "m4v" -> "video/mp4"; "webm" -> "video/webm"; "mov" -> "video/quicktime"; "3gp" -> "video/3gpp"; "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "ogg", "oga" -> "audio/ogg"; "wav" -> "audio/wav"; "flac" -> "audio/flac"
        "pdf" -> "application/pdf"; "zip" -> "application/zip"; "apk" -> "application/vnd.android.package-archive"; "txt" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"; "m3u8" -> "application/vnd.apple.mpegurl"
        else -> "application/octet-stream"
    }

    private fun extensionForMime(mime: String): String = when (mime) {
        "video/mp4" -> ".mp4"; "video/webm" -> ".webm"; "video/quicktime" -> ".mov"; "video/3gpp" -> ".3gp"; "video/x-matroska" -> ".mkv"
        "audio/mpeg" -> ".mp3"; "audio/mp4" -> ".m4a"; "audio/ogg" -> ".ogg"; "audio/wav" -> ".wav"; "audio/flac" -> ".flac"
        "application/pdf" -> ".pdf"; "application/zip" -> ".zip"; "application/vnd.android.package-archive" -> ".apk"; "text/plain" -> ".txt"
        "image/jpeg" -> ".jpg"; "image/png" -> ".png"; "image/gif" -> ".gif"; "image/webp" -> ".webp"; "application/vnd.apple.mpegurl" -> ".m3u8"
        else -> ".bin"
    }

    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)) }

    private fun notification(title: String, bytes: Long, total: Long?, id: String?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(title).setOnlyAlertOnce(true).setOngoing(total == null || bytes < (total ?: Long.MAX_VALUE))
        if (total != null && total > 0L) builder.setProgress(100, ((bytes * 100L) / total).toInt().coerceIn(0, 100), false)
        if (id != null) {
            val pause = PendingIntent.getService(this, id.hashCode(), Intent(this, DownloadService::class.java).apply { action = ACTION_PAUSE; putExtra(EXTRA_ID, id) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val cancel = PendingIntent.getService(this, id.hashCode() + 1, Intent(this, DownloadService::class.java).apply { action = ACTION_CANCEL; putExtra(EXTRA_ID, id) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(NotificationCompat.Action(0, "Pause", pause)).addAction(NotificationCompat.Action(0, "Cancel", cancel))
        }
        return builder.build()
    }

    private fun updateNotification(title: String, bytes: Long, total: Long, id: String, done: Boolean) { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(title, bytes, if (total > 0L) total else null, if (done) null else id)) }

    @RequiresApi(35)
    override fun onTimeout(startId: Int, fgsType: Int) { stopRequested = true; worker?.interrupt(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { stopRequested = true; worker?.interrupt(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
