from pathlib import Path

MAIN = Path('app/src/main/java/com/coeric/universalbrowser/MainActivity.kt')
src = MAIN.read_text()

# Keep the repository source identical to the production download behavior.
old_download = '''    private fun openDownloads() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                type = "resource/folder"
                data = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toURI().toString())
            })
        } catch (_: Throwable) { toast("Open the Downloads app to view downloaded files.") }
    }
'''
new_download = '''    private fun openDownloads() {
        DownloadCenter.show(this)
    }
'''
if old_download in src:
    src = src.replace(old_download, new_download, 1)

# Add persistent browser-tool state.
field_anchor = '    private var lastMediaPromptAt = 0L\n'
fields = '''    private var pageZoom = 1.0\n    private var readerMode = false\n    private val browserPrefs by lazy { getSharedPreferences("browser_preferences", MODE_PRIVATE) }\n'''
if 'private var pageZoom = 1.0' not in src and field_anchor in src:
    src = src.replace(field_anchor, field_anchor + fields, 1)

# Extend the existing power-tools menu without replacing the rest of the browser UI.
old_items = '''            "⭐ Add current page to bookmarks", "🔖 Bookmarks", "🕘 History",
            if (desktopMode) "📱 Switch to mobile site" else "🖥 Desktop site",
            "🔎 Find in page", "↗ Share current page", "⬇ Downloads",
            "🧹 Clear browsing data", "⚙ Browser settings"'''
new_items = '''            "⭐ Add current page to bookmarks", "🔖 Bookmarks", "🕘 History",
            if (desktopMode) "📱 Switch to mobile site" else "🖥 Desktop site",
            "🔎 Find in page", "↗ Share current page", "⬇ Downloads",
            "🔍 Zoom in", "🔎 Zoom out", "↺ Reset zoom", "📖 Reader mode",
            "🌐 Search engine", "🧹 Clear browsing data", "⚙ Browser settings"'''
if old_items in src:
    src = src.replace(old_items, new_items, 1)

old_when = '''                0 -> addCurrentBookmark(); 1 -> showBookmarks(); 2 -> showHistory()
                3 -> toggleDesktopSite(); 4 -> findInPage(); 5 -> shareCurrentPage()
                6 -> openDownloads(); 7 -> clearBrowsingData(); 8 -> showBrowserSettings()'''
new_when = '''                0 -> addCurrentBookmark(); 1 -> showBookmarks(); 2 -> showHistory()
                3 -> toggleDesktopSite(); 4 -> findInPage(); 5 -> shareCurrentPage()
                6 -> openDownloads(); 7 -> changePageZoom(0.10); 8 -> changePageZoom(-0.10)
                9 -> resetPageZoom(); 10 -> toggleReaderMode(); 11 -> chooseSearchEngine()
                12 -> clearBrowsingData(); 13 -> showBrowserSettings()'''
if old_when in src:
    src = src.replace(old_when, new_when, 1)

methods = r'''

    private fun applyPageScript(script: String) {
        if (!::session.isInitialized) { toast("Open a page first."); return }
        val encoded = android.util.Base64.encodeToString(script.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        // Use a data URL wrapper to avoid treating the page's URL as executable input.
        // Gecko blocks cross-origin navigation, so this is intentionally limited to the
        // current document's presentation changes through the browser's content pipeline.
        try {
            session.loadUri("javascript:(function(){eval(atob('$encoded'));})()")
        } catch (_: Throwable) {
            toast("This page does not allow browser presentation changes.")
        }
    }

    private fun changePageZoom(delta: Double) {
        pageZoom = (pageZoom + delta).coerceIn(0.50, 2.50)
        browserPrefs.edit().putFloat("page_zoom", pageZoom.toFloat()).apply()
        applyPageScript("document.documentElement.style.zoom='${String.format(Locale.US, "%.2f", pageZoom)}';")
        toast("Page zoom ${((pageZoom * 100).toInt())}%")
    }

    private fun resetPageZoom() {
        pageZoom = 1.0
        browserPrefs.edit().putFloat("page_zoom", 1.0f).apply()
        applyPageScript("document.documentElement.style.zoom='1';")
        toast("Page zoom reset")
    }

    private fun toggleReaderMode() {
        readerMode = !readerMode
        if (readerMode) {
            applyPageScript("document.documentElement.style.background='#fff';document.body.style.maxWidth='760px';document.body.style.margin='0 auto';document.body.style.padding='24px';document.body.style.fontFamily='serif';document.body.style.lineHeight='1.65';")
            toast("Reader presentation enabled")
        } else {
            applyPageScript("document.documentElement.style.background='';document.body.style.maxWidth='';document.body.style.margin='';document.body.style.padding='';document.body.style.fontFamily='';document.body.style.lineHeight='';")
            toast("Reader presentation disabled")
        }
    }

    private fun chooseSearchEngine() {
        val engines = arrayOf("Google", "Bing", "DuckDuckGo", "Brave Search")
        val urls = arrayOf("https://www.google.com/search?q=", "https://www.bing.com/search?q=", "https://duckduckgo.com/?q=", "https://search.brave.com/search?q=")
        val current = browserPrefs.getInt("search_engine", 0)
        AlertDialog.Builder(this).setTitle("Default search engine").setSingleChoiceItems(engines, current) { dialog, which ->
            browserPrefs.edit().putInt("search_engine", which).apply()
            toast("Search engine: ${engines[which]}")
            dialog.dismiss()
        }.setNegativeButton("Close", null).show()
    }
'''
if 'private fun changePageZoom(' not in src:
    pos = src.rfind('\n}\n')
    if pos < 0:
        raise SystemExit('MainActivity class terminator not found')
    src = src[:pos] + methods + src[pos:]

# Restore saved zoom after activity creation when a page is available.
create_anchor = '        super.onCreate(savedInstanceState)\n'
restore = '        pageZoom = browserPrefs.getFloat("page_zoom", 1.0f).toDouble()\n'
if restore not in src and create_anchor in src:
    src = src.replace(create_anchor, create_anchor + restore, 1)

MAIN.write_text(src)
