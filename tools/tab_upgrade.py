from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/coeric/universalbrowser/MainActivity.kt')
src = MAIN.read_text()


def once(needle: str, replacement: str, label: str):
    global src
    if replacement in src:
        return
    count = src.count(needle)
    if count != 1:
        raise SystemExit(f'Tab upgrade anchor for {label} expected exactly once, found {count}')
    src = src.replace(needle, replacement, 1)


once(
    '    private lateinit var session: GeckoSession\n',
    '    private lateinit var session: GeckoSession\n    private lateinit var tabManager: BrowserTabManager\n',
    'tab manager field'
)

once(
    '        super.onCreate(savedInstanceState)\n',
    '        super.onCreate(savedInstanceState)\n        tabManager = BrowserTabManager(getRuntime())\n',
    'tab manager initialization'
)

# Replace the single-session bootstrap with a reusable binder. The tab manager
# owns GeckoSession lifetimes; MainActivity remains responsible for delegates/UI.
pattern = re.compile(r'    private fun ensureBrowserReady\(\) \{.*?\n    private fun installMediaDetector\(\)', re.S)
match = pattern.search(src)
if not match:
    raise SystemExit('Could not locate ensureBrowserReady block')

replacement = '''    private fun ensureBrowserReady() {
        if (tabManager.count() == 0) tabManager.create(privateMode = false)
        val active = tabManager.active() ?: return
        bindSession(active.session)
    }

    private fun bindSession(target: GeckoSession) {
        session = target
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(crashedSession: GeckoSession) { recoverSession("The page process stopped and was restarted.") }
            override fun onKill(killedSession: GeckoSession) { recoverSession("The page process was stopped by Android and was restarted.") }
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tabManager.updateLabel(session, title ?: "New tab")
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, value: Int) {
                if (session !== this@MainActivity.session) return
                progress.progress = value
                progress.visibility = if (value in 1..99) View.VISIBLE else View.GONE
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                tabManager.updateLabel(session, url ?: "New tab")
                if (session !== this@MainActivity.session) return
                currentUrl = url ?: currentUrl
                if (!url.isNullOrBlank()) browserData.recordVisit(url, url)
                addressBar.setText(url ?: "")
            }
            override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                if (session === this@MainActivity.session) { canGoBack = value; backButton.alpha = if (value) 1f else 0.35f }
            }
            override fun onCanGoForward(session: GeckoSession, value: Boolean) {
                if (session === this@MainActivity.session) { canGoForward = value; forwardButton.alpha = if (value) 1f else 0.35f }
            }
        }
        getRuntime().webExtensionController.promptDelegate = extensionPromptDelegate
        if (!session.isOpen) session.open(getRuntime())
        browserView.setSession(session)
        tabManager.activate(tabManager.indexOf(session))
        installMediaDetector()
    }

    private fun installMediaDetector()'''

src = src[:match.start()] + replacement + src[match.end():]

# Add a dedicated tab button beside reload and the menu.
once(
    '        row.addView(toolbarButton("↻", 21f) { if (::session.isInitialized && browserView.visibility == View.VISIBLE) session.reload() else showHome() })\n        row.addView(toolbarButton("⋮", 23f) { showBrowserMenu() })',
    '        row.addView(toolbarButton("↻", 21f) { if (::session.isInitialized && browserView.visibility == View.VISIBLE) session.reload() else showHome() })\n        row.addView(toolbarButton("▢", 18f) { showTabs() })\n        row.addView(toolbarButton("⋮", 23f) { showBrowserMenu() })',
    'tab toolbar button'
)

# Extend the main menu with tab operations.
once(
    '        val items = arrayOf("Home", "Extensions", "Web Stores", "Check Chrome extension", "Browse Add-ons", "Media Downloads", "Reload", "About Universal")',
    '        val items = arrayOf("Home", "Tabs", "New tab", "Private tab", "Extensions", "Web Stores", "Check Chrome extension", "Browse Add-ons", "Media Downloads", "Reload", "About Universal")',
    'tab menu items'
)
once(
    '''                0 -> showHome()
                1 -> showExtensionManagerPage()
                2 -> showWebStores()
                3 -> openExtensionPicker()
                4 -> navigate("https://addons.mozilla.org/android/")
                5 -> showMediaDownloadInfo()
                6 -> if (::session.isInitialized && browserView.visibility == View.VISIBLE) session.reload()
                7 -> showAbout()''',
    '''                0 -> showHome()
                1 -> showTabs()
                2 -> openNewTab(false)
                3 -> openNewTab(true)
                4 -> showExtensionManagerPage()
                5 -> showWebStores()
                6 -> openExtensionPicker()
                7 -> navigate("https://addons.mozilla.org/android/")
                8 -> showMediaDownloadInfo()
                9 -> if (::session.isInitialized && browserView.visibility == View.VISIBLE) session.reload()
                10 -> showAbout()''',
    'tab menu actions'
)

# Insert the actual tab UI/lifecycle operations before the browser menu.
if 'private fun showTabs()' not in src:
    marker = '    private fun showBrowserMenu() {'
    methods = '''    private fun showTabs() {
        val tabs = tabManager.all()
        if (tabs.isEmpty()) {
            openNewTab(false)
            return
        }
        val labels = tabs.mapIndexed { index, tab ->
            val marker = if (tab.session === session) "● " else "○ "
            val mode = if (tab.privateMode) "Private" else "Tab"
            "$marker$mode ${index + 1}: ${tab.label.take(70)}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Tabs (${tabs.size})")
            .setItems(labels) { _, which -> switchToTab(which) }
            .setNeutralButton("New tab") { _, _ -> openNewTab(false) }
            .setNegativeButton("Private") { _, _ -> openNewTab(true) }
            .setPositiveButton("Close current") { _, _ -> closeCurrentTab() }
            .show()
    }

    private fun openNewTab(privateMode: Boolean) {
        val tab = tabManager.create(privateMode)
        bindSession(tab.session)
        tab.session.setActive(true)
        homePanel.visibility = View.VISIBLE
        browserView.visibility = View.GONE
        addressBar.setText("")
        currentUrl = ""
        canGoBack = false
        canGoForward = false
        toast(if (privateMode) "Private tab created" else "New tab created")
    }

    private fun switchToTab(index: Int) {
        val tab = tabManager.activate(index) ?: return
        bindSession(tab.session)
        val url = tab.session.toString().let { currentUrl }.takeIf { currentUrl.isNotBlank() }
        if (url.isNullOrBlank()) {
            homePanel.visibility = View.VISIBLE
            browserView.visibility = View.GONE
            addressBar.setText("")
        } else {
            homePanel.visibility = View.GONE
            browserView.visibility = View.VISIBLE
            addressBar.setText(currentUrl)
        }
        toast("Switched to tab ${index + 1}")
    }

    private fun closeCurrentTab() {
        if (tabManager.count() == 0) return
        val next = tabManager.close(tabManager.indexOf(session))
        if (next == null) {
            if (::session.isInitialized) session = GeckoSession()
            showHome()
            currentUrl = ""
            canGoBack = false
            canGoForward = false
            return
        }
        bindSession(next.session)
        if (next.session.isOpen && currentUrl.isNotBlank()) {
            homePanel.visibility = View.GONE
            browserView.visibility = View.VISIBLE
        } else {
            showHome()
        }
        toast("Tab closed")
    }

'''
    if marker not in src:
        raise SystemExit('Browser menu marker not found')
    src = src.replace(marker, methods + marker, 1)

required = [
    'private lateinit var tabManager: BrowserTabManager',
    'tabManager = BrowserTabManager(getRuntime())',
    'private fun bindSession(target: GeckoSession)',
    'private fun showTabs()',
    'private fun openNewTab(privateMode: Boolean)',
    'private fun switchToTab(index: Int)',
    'private fun closeCurrentTab()',
    'toolbarButton("▢", 18f)',
]
missing = [x for x in required if x not in src]
if missing:
    raise SystemExit('Tab upgrade incomplete; missing: ' + ', '.join(missing))

MAIN.write_text(src)
