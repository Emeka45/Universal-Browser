from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = ROOT / "app/src/main/java/com/coeric/universalbrowser/MainActivity.kt"
text = main.read_text()

old_classifier = '''    private fun isDownloadableMediaUrl(url: String): Boolean {
        if (url.isBlank() || url.startsWith("blob:", true) || url.startsWith("data:", true)) return false
        val lower = url.substringBefore('?').lowercase(Locale.US)
        return listOf("mp4", "webm", "mov", "m4v", "3gp", "mkv", "mp3", "m4a", "ogg", "oga").any { lower.endsWith(".$it") }
    }
'''
new_classifier = '''    private fun isDownloadableMediaUrl(url: String): Boolean = AdvancedMediaDownloadEngine.isSupported(url)
'''
if old_classifier in text:
    text = text.replace(old_classifier, new_classifier)

old_enqueue = '''    private fun enqueueMediaDownload(url: String, title: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(title.ifBlank { "Universal media" })
                .setDescription("Downloaded by Universal Browser")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true).setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, buildMediaFilename(url, title))
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Cookie", it) }
            if (currentUrl.isNotBlank()) request.addRequestHeader("Referer", currentUrl)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast("Download started — check Downloads for progress.")
        } catch (error: Throwable) { toast("Could not start download: ${error.message ?: "unsupported media"}") }
    }
'''
new_enqueue = '''    private fun enqueueMediaDownload(url: String, title: String) {
        AdvancedMediaDownloadEngine.enqueue(
            context = this,
            url = url,
            title = title,
            referer = currentUrl,
            onStarted = { runOnUiThread { toast("Media download started") } },
            onFinished = { result -> runOnUiThread { toast("${result.kind} download saved: ${result.fileName}") } },
            onError = { error -> runOnUiThread { toast("Media download failed: $error") } }
        )
    }
'''
if old_enqueue in text:
    text = text.replace(old_enqueue, new_enqueue)

text = text.replace(
    'Universal detects supported direct media resources and hands downloads to Android\'s Download Manager. DRM-protected, blob-only and protected streams cannot be downloaded by this feature.',
    'Universal detects direct media plus non-DRM HLS/DASH manifests. Direct files use Android Download Manager; ordinary HLS segments can be assembled locally. DRM/encrypted streams and blob-only resources remain protected.'
)
main.write_text(text)

# Keep the existing imports harmlessly compatible; this removes no functionality.
print("Universal Browser media integration applied.")
