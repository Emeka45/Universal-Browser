package com.coeric.universalbrowser

import android.app.Activity
import android.app.Application
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import java.util.Locale

/** Quetta-style media discovery surface: detect playable sources and offer quality/download choices. */
object MediaBridge {
    private const val EXTENSION_URI = "resource://android/assets/universal_media/"
    private const val EXTENSION_ID = "media-detector@universalbrowser"
    private const val NATIVE_APP = "browser"
    private const val DIALOG_DEBOUNCE_MS = 12_000L

    private var currentActivity: Activity? = null
    private var extension: WebExtension? = null
    private var lastDialogKey = ""
    private var lastDialogAt = 0L

    private val delegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            if (nativeApp != NATIVE_APP || message !is JSONObject) return null
            if (message.optString("type") != "media_detected") return null
            val videos = message.optJSONArray("videos") ?: return null
            val pageUrl = message.optString("pageUrl")
            val pageTitle = message.optString("pageTitle").ifBlank { "Universal media" }
            val activity = currentActivity ?: return null
            activity.runOnUiThread { showMediaChoices(activity, pageTitle, pageUrl, videos) }
            return null
        }
    }

    fun initialize(application: Application, runtime: GeckoRuntime) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { currentActivity = activity }
            override fun onActivityPaused(activity: Activity) { if (currentActivity === activity) currentActivity = null }
            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) { if (currentActivity === activity) currentActivity = null }
        })
        runtime.webExtensionController.ensureBuiltIn(EXTENSION_URI, EXTENSION_ID).accept(
            { ext -> extension = ext; ext?.setMessageDelegate(delegate, NATIVE_APP) },
            { error -> android.util.Log.e("MediaBridge", "Media detector install failed", error) }
        )
    }

    private fun showMediaChoices(activity: Activity, title: String, referer: String, videos: JSONArray) {
        if (activity.isFinishing || activity.isDestroyed) return
        val candidates = linkedMapOf<String, MediaCandidate>()
        for (i in 0 until videos.length()) {
            val item = videos.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (url.isBlank() || candidates.containsKey(url)) continue
            candidates[url] = MediaCandidate(url, item.optString("kind", "video"), item.optInt("width"), item.optInt("height"), item.optString("title").ifBlank { title })
        }
        if (candidates.isEmpty()) return
        val key = candidates.keys.sorted().joinToString("|")
        val now = System.currentTimeMillis()
        if (key == lastDialogKey && now - lastDialogAt < DIALOG_DEBOUNCE_MS) return
        lastDialogKey = key
        lastDialogAt = now

        val list = candidates.values.sortedWith(compareByDescending<MediaCandidate> { it.height }.thenBy { it.url }).take(12)
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 4, 8, 8) }
        root.addView(TextView(activity).apply { text = "Media found on this page. Select a source or stream quality."; textSize = 13f; setPadding(12, 8, 12, 12) })
        list.forEach { candidate ->
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(12, 10, 8, 10); setBackgroundColor(0xFFF7F6FB.toInt()) }
            val label = TextView(activity).apply { text = qualityLabel(candidate); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setPadding(4, 0, 8, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            val download = TextView(activity).apply { text = if (candidate.kind == "stream") "Qualities" else "Download"; textSize = 13f; setTextColor(0xFF6548FF.toInt()); setPadding(14, 8, 14, 8); setOnClickListener { if (candidate.kind == "stream" && candidate.url.substringBefore('?').lowercase(Locale.US).endsWith(".m3u8")) showStreamQualities(activity, candidate, referer) else startDownload(activity, candidate, referer) } }
            row.addView(label); row.addView(download); root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6 })
        }
        android.app.AlertDialog.Builder(activity).setTitle("Media detected").setView(root).setNegativeButton("Not now", null).show()
    }

    private fun showStreamQualities(activity: Activity, candidate: MediaCandidate, referer: String) {
        Toast.makeText(activity, "Reading available video qualities…", Toast.LENGTH_SHORT).show()
        AdvancedMediaDownloadEngine.inspectHlsVariants(candidate.url, referer,
            onReady = { variants ->
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (variants.isEmpty()) { startDownload(activity, candidate, referer); return@runOnUiThread }
                    val labels = variants.map { variant ->
                        val resolution = if (variant.height > 0) "${variant.height}p" else if (variant.width > 0) "${variant.width}p" else "Auto"
                        val bandwidth = if (variant.bandwidth > 0) " • ${variant.bandwidth / 1000} kbps" else ""
                        "$resolution$bandwidth"
                    }.toTypedArray()
                    android.app.AlertDialog.Builder(activity)
                        .setTitle("Choose video quality")
                        .setItems(labels) { _, which -> startStreamDownload(activity, candidate, variants[which].url, referer, labels[which]) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            },
            onError = { error -> activity.runOnUiThread { Toast.makeText(activity, "Quality detection failed: $error", Toast.LENGTH_SHORT).show() } }
        )
    }

    private fun startStreamDownload(activity: Activity, candidate: MediaCandidate, streamUrl: String, referer: String, quality: String) {
        Toast.makeText(activity, "Starting $quality download…", Toast.LENGTH_SHORT).show()
        startDownload(activity, candidate.copy(url = streamUrl), referer)
    }

    private fun startDownload(activity: Activity, candidate: MediaCandidate, referer: String) {
        val baseTitle = candidate.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).ifBlank { "Universal-video" }
        val url = candidate.url
        val path = url.substringBefore('?').lowercase(Locale.US)
        val stream = path.endsWith(".m3u8") || path.endsWith(".mpd")
        val extension = when {
            path.endsWith(".mp4") -> ".mp4"
            path.endsWith(".webm") -> ".webm"
            path.endsWith(".mov") -> ".mov"
            path.endsWith(".m4v") -> ".m4v"
            path.endsWith(".mp3") -> ".mp3"
            path.endsWith(".m4a") -> ".m4a"
            path.endsWith(".ogg") || path.endsWith(".oga") -> ".ogg"
            else -> ".mp4"
        }
        val title = if (baseTitle.contains('.')) baseTitle else baseTitle + extension
        try {
            if (stream) {
                AdvancedMediaDownloadEngine.enqueue(activity, url, title, referer,
                    onStarted = { activity.runOnUiThread { toast(activity, "Media download started") } },
                    onFinished = { result -> activity.runOnUiThread { toast(activity, "Saved ${result.fileName}") } },
                    onError = { error -> activity.runOnUiThread { toast(activity, "Download failed: $error") } }
                )
            } else {
                val id = "media-${System.currentTimeMillis()}"
                DownloadTaskStore(activity).upsert(DownloadTaskStore.Task(id, url, title, "queued", 0L, -1L, System.currentTimeMillis(), referer))
                DownloadService.start(activity, id, url, title, referer)
                toast(activity, "Download started")
            }
        } catch (error: Throwable) { toast(activity, "Download failed: ${error.message ?: "unknown error"}") }
    }

    private fun qualityLabel(candidate: MediaCandidate): String {
        val resolution = if (candidate.height > 0) "${candidate.height}p" else if (candidate.width > 0) "${candidate.width}px" else "Auto"
        val type = if (candidate.kind == "stream") "Stream" else "Video"
        return "$resolution • $type"
    }

    private fun toast(activity: Activity, message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

    private data class MediaCandidate(val url: String, val kind: String, val width: Int, val height: Int, val title: String)
}
