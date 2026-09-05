from pathlib import Path

main = Path('app/src/main/java/com/coeric/universalbrowser/MainActivity.kt')
src = main.read_text()
old = '''    private fun openDownloads() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                type = "resource/folder"
                data = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toURI().toString())
            })
        } catch (_: Throwable) { toast("Open the Downloads app to view downloaded files.") }
    }
'''
new = '''    private fun openDownloads() {
        DownloadCenter.show(this)
    }
'''
if old not in src:
    raise SystemExit('download launcher anchor not found')
main.write_text(src.replace(old, new, 1))
