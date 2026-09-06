from pathlib import Path

MAIN = Path('app/src/main/java/com/coeric/universalbrowser/MainActivity.kt')
src = MAIN.read_text()


def replace_required(needle: str, replacement: str, label: str):
    global src
    if replacement in src:
        return
    count = src.count(needle)
    if count != 1:
        raise SystemExit(f'Product upgrade anchor for {label} expected exactly once, found {count}')
    src = src.replace(needle, replacement, 1)


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
replace_required(old_download, new_download, 'Download Center integration')

# Add persistent browser-tool state.
field_anchor = '    private var lastMediaPromptAt = 0L\n'
fields = '''    private var pageZoom = 1.0\n    private var readerMode = false\n    private val browserPrefs by lazy { getSharedPreferences("browser_preferences", MODE_PRIVATE) }\n'''
replace_required(field_anchor, field_anchor + fields, 'browser tool state')

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
replace_required(old_items, new_items, 'power-tools menu')

old_when = '''                0 -> addCurrentBookmark(); 1 -> showBookmarks(); 2 -> showHistory()
                3 -> toggleDesktopSite(); 4 -> findInPage(); 5 -> shareCurrentPage()
                6 -> openDownloads(); 7 -> clearBrowsingData(); 8 -> showBrowserSettings()'''
new_when = '''                0 -> addCurrentBookmark(); 1 -> showBookmarks(); 2 -> showHistory()
                3 -> toggleDesktopSite(); 4 -> findInPage(); 5 -> shareCurrentPage()
                6 -> openDownloads(); 7 -> changePageZoom(0.10); 8 -> changePageZoom(-0.10)
                9 -> resetPageZoom(); 10 -> toggleReaderMode(); 11 -> chooseSearchEngine()
                12 -> clearBrowsingData(); 13 -> showBrowserSettings()'''
replace_required(old_when, new_when, 'power-tools actions')

methods = r'''

    private fun applyPageScript(script: String) {
        if (!::session.isInitialized) { toast("Open a page first."); return }
        val encoded = android.util.Base64.encodeToString(script.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
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

# Restore saved zoom after activity creation.
create_anchor = '        super.onCreate(savedInstanceState)\n'
restore = '        pageZoom = browserPrefs.getFloat("page_zoom", 1.0f).toDouble()\n'
replace_required(create_anchor, create_anchor + restore, 'saved zoom restoration')

# Fail closed if any expected product feature is missing after transformation.
required = [
    'DownloadCenter.show(this)',
    'private var pageZoom = 1.0',
    'private fun changePageZoom(delta: Double)',
    'private fun resetPageZoom()',
    'private fun toggleReaderMode()',
    'private fun chooseSearchEngine()',
    '"🔍 Zoom in"',
    '"📖 Reader mode"',
]
missing = [needle for needle in required if needle not in src]
if missing:
    raise SystemExit('Product upgrade incomplete; missing: ' + ', '.join(missing))

MAIN.write_text(src)
