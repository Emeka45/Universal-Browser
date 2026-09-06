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

# Extend the power-tools menu with native browser capabilities.
old_items = '''            "⭐ Add current page to bookmarks", "🔖 Bookmarks", "🕘 History",
            if (desktopMode) "📱 Switch to mobile site" else "🖥 Desktop site",
            "🔎 Find in page", "↗ Share current page", "⬇ Downloads",
            "🧹 Clear browsing data", "⚙ Browser settings"'''
new_items = '''            "⭐ Add current page to bookmarks", "🔖 Bookmarks", "🕘 History",
            if (desktopMode) "📱 Switch to mobile site" else "🖥 Desktop site",
            "🔎 Find in page", "↗ Share current page", "⬇ Downloads",
            "🔍 Zoom in", "🔎 Zoom out", "↺ Reset zoom", "📖 Reader mode",
            "🌐 Search engine", "📄 Save as PDF", "🖨 Print page", "📸 Screenshot",
            "🛡 Site controls", "🧹 Clear browsing data", "⚙ Browser settings"'''
replace_required(old_items, new_items, 'power-tools menu')

old_when = '''                0 -> addCurrentBookmark(); 1 -> showBookmarks(); 2 -> showHistory()
                3 -> toggleDesktopSite(); 4 -> findInPage(); 5 -> shareCurrentPage()
                6 -> openDownloads(); 7 -> clearBrowsingData(); 8 -> showBrowserSettings()'''
new_when = '''                0 -> addCurrentBookmark(); 1 -> showBookmarks(); 2 -> showHistory()
                3 -> toggleDesktopSite(); 4 -> findInPage(); 5 -> shareCurrentPage()
                6 -> openDownloads(); 7 -> changePageZoom(0.10); 8 -> changePageZoom(-0.10)
                9 -> resetPageZoom(); 10 -> toggleReaderMode(); 11 -> chooseSearchEngine()
                12 -> saveCurrentPageAsPdf(); 13 -> printCurrentPage(); 14 -> capturePageScreenshot()
                15 -> showCurrentSiteControls(); 16 -> clearBrowsingData(); 17 -> showBrowserSettings()'''
replace_required(old_when, new_when, 'power-tools actions')

# Use GeckoView's native settings for tracking protection and the real desktop viewport.
session_anchor = '        session = GeckoSession()\n'
replace_required(session_anchor, '''        session = GeckoSession(org.mozilla.geckoview.GeckoSessionSettings.Builder()
            .useTrackingProtection(browserPrefs.getBoolean("tracking_protection", true))
            .userAgentMode(if (desktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktopMode) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build())
''', 'native Gecko session settings')

# Make search-engine selection actually control searches from the address bar.
old_search = '            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(input, "UTF-8")}"\n'
new_search = '            else -> buildSearchUrl(input)\n'
replace_required(old_search, new_search, 'search engine routing')

methods = r'''

    private fun buildSearchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return when (browserPrefs.getInt("search_engine", 0)) {
            1 -> "https://www.bing.com/search?q=$encoded"
            2 -> "https://duckduckgo.com/?q=$encoded"
            3 -> "https://search.brave.com/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    private fun saveCurrentPageAsPdf() {
        if (!::session.isInitialized || currentUrl.isBlank()) { toast("Open a page first."); return }
        BrowserFeatureCenter.savePageAsPdf(this, session)
    }

    private fun printCurrentPage() {
        if (!::session.isInitialized || currentUrl.isBlank()) { toast("Open a page first."); return }
        BrowserFeatureCenter.printPage(this, session)
    }

    private fun capturePageScreenshot() {
        if (!::session.isInitialized || currentUrl.isBlank()) { toast("Open a page first."); return }
        BrowserFeatureCenter.captureVisiblePage(this, session)
    }

    private fun showCurrentSiteControls() {
        if (!::session.isInitialized || currentUrl.isBlank()) { toast("Open a page first."); return }
        BrowserFeatureCenter.showSiteControls(this, session, currentUrl)
    }
'''
if 'private fun saveCurrentPageAsPdf()' not in src:
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
    'private fun buildSearchUrl(query: String)',
    'private fun saveCurrentPageAsPdf()',
    'private fun printCurrentPage()',
    'private fun capturePageScreenshot()',
    'private fun showCurrentSiteControls()',
    '"🔍 Zoom in"',
    '"📖 Reader mode"',
    '"📄 Save as PDF"',
    '"📸 Screenshot"',
    '.useTrackingProtection(',
]
missing = [needle for needle in required if needle not in src]
if missing:
    raise SystemExit('Product upgrade incomplete; missing: ' + ', '.join(missing))

MAIN.write_text(src)
