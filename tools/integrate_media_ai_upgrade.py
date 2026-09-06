from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
main = ROOT / "app/src/main/java/com/coeric/universalbrowser/MainActivity.kt"
text = main.read_text()

classifier = re.compile(r'    private fun isDownloadableMediaUrl\(url: String\): Boolean \{.*?\n    \}\n', re.S)
replacement_classifier = '''    private fun isDownloadableMediaUrl(url: String): Boolean =
        AdvancedMediaDownloadEngine.isSupported(url)
'''
text, classifier_count = classifier.subn(replacement_classifier, text, count=1)
if classifier_count == 0 and 'AdvancedMediaDownloadEngine.isSupported(url)' not in text:
    raise SystemExit('Could not locate media classifier in MainActivity.kt')

enqueue = re.compile(r'    private fun enqueueMediaDownload\(url: String, title: String\) \{.*?\n    \}\n', re.S)
replacement_enqueue = '''    private fun enqueueMediaDownload(url: String, title: String) {
        AdvancedMediaDownloadEngine.enqueue(
            context = this,
            url = url,
            title = title,
            referer = currentUrl,
            onStarted = { runOnUiThread { toast("Media download started") } },
            onFinished = { result ->
                runOnUiThread { toast("${result.kind} download saved: ${result.fileName}") }
            },
            onError = { error -> runOnUiThread { toast("Media download failed: $error") } }
        )
    }
'''
text, enqueue_count = enqueue.subn(replacement_enqueue, text, count=1)
if enqueue_count == 0 and 'AdvancedMediaDownloadEngine.enqueue(' not in text:
    raise SystemExit('Could not locate media enqueue method in MainActivity.kt')

text = text.replace(
    "Universal detects supported direct media resources and hands downloads to Android's Download Manager. DRM-protected, blob-only and protected streams cannot be downloaded by this feature.",
    "Universal detects direct media plus non-DRM HLS/DASH manifests. Direct files use Android Download Manager; ordinary HLS segments can be assembled locally. DRM/encrypted streams and blob-only resources remain protected."
)
main.write_text(text)
print("Universal Browser media integration applied.")
