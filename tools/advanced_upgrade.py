from pathlib import Path

MAIN = Path('app/src/main/java/com/coeric/universalbrowser/MainActivity.kt')
src = MAIN.read_text()


def replace_once(needle: str, replacement: str, label: str):
    global src
    if replacement.strip() in src:
        return
    count = src.count(needle)
    if count != 1:
        raise SystemExit(f'Advanced upgrade anchor for {label} expected exactly once, found {count}')
    src = src.replace(needle, replacement, 1)

# Private-window state. Each private window gets its own GeckoSession configured
# with GeckoView's native private-mode setting; no browsing history is recorded
# by our application data store while privateMode is active.
replace_once(
    '    private var lastMediaPromptAt = 0L\n',
    '    private var lastMediaPromptAt = 0L\n    private val privateMode by lazy { intent.getBooleanExtra("PRIVATE_MODE", false) }\n',
    'private window state'
)

# Make private mode a real Gecko session setting, not a UI-only label.
replace_once(
    '            .useTrackingProtection(browserPrefs.getBoolean("tracking_protection", true))\n',
    '            .useTrackingProtection(browserPrefs.getBoolean("tracking_protection", true))\n            .usePrivateMode(privateMode)\n',
    'native private session'
)

# Allow launcher/deep-link style intents created by Add-to-home-screen shortcuts
# to open a specific page inside Universal.
replace_once(
    '        setContentView(root)\n',
    '        setContentView(root)\n        intent.getStringExtra("START_URL")?.takeIf { it.isNotBlank() }?.let { navigate(it) }\n',
    'shortcut start URL'
)

# Expand the existing Power Tools list with the next production browser actions.
old_items = '''            "🛡 Site controls", "🧹 Clear browsing data", "⚙ Browser settings"'''
new_items = '''            "🛡 Site controls", "🧹 Clear browsing data", "⚙ Browser settings",
            "🕵 New private window", "🌍 Translate page", "➕ Add to home screen",
            "🔐 Clear site data", "🔒 Connection information"'''
replace_once(old_items, new_items, 'advanced power-tools menu')

old_when = '''                15 -> showCurrentSiteControls(); 16 -> clearBrowsingData(); 17 -> showBrowserSettings()'''
new_when = '''                15 -> showCurrentSiteControls(); 16 -> clearBrowsingData(); 17 -> showBrowserSettings()
                18 -> openPrivateWindow(); 19 -> translateCurrentPage(); 20 -> addCurrentPageToHome()
                21 -> clearCurrentSiteData(); 22 -> showConnectionInformation()'''
replace_once(old_when, new_when, 'advanced power-tools actions')

advanced_methods = r'''

    private fun openPrivateWindow() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra("PRIVATE_MODE", true)
        })
    }

    private fun translateCurrentPage() {
        if (currentUrl.isBlank()) { toast("Open a page first."); return }
        val encoded = java.net.URLEncoder.encode(currentUrl, "UTF-8")
        navigate("https://translate.google.com/translate?sl=auto&tl=en&u=$encoded")
    }

    private fun addCurrentPageToHome() {
        if (currentUrl.isBlank()) { toast("Open a page first."); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { toast("Home shortcuts are not supported on this Android version."); return }
        val shortcutManager = getSystemService(android.content.pm.ShortcutManager::class.java)
        val title = currentUrl.removePrefix("https://").removePrefix("http://").substringBefore('/').take(40).ifBlank { "Universal page" }
        val shortcut = android.content.pm.ShortcutInfo.Builder(this, "page-${System.currentTimeMillis()}")
            .setShortLabel(title)
            .setLongLabel("Open $title in Universal Browser")
            .setIntent(Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("START_URL", currentUrl)
            })
            .build()
        shortcutManager?.requestPinShortcut(shortcut, null)
        toast("Home-screen shortcut requested")
    }

    private fun clearCurrentSiteData() {
        val host = try { Uri.parse(currentUrl).host ?: "this site" } catch (_: Throwable) { "this site" }
        AlertDialog.Builder(this).setTitle("Clear site data")
            .setMessage("Remove cookies and web storage for $host? This may sign you out.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                CookieManager.getInstance().removeAllCookies {
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().flush()
                    toast("Site data cleared")
                }
            }.show()
    }

    private fun showConnectionInformation() {
        if (currentUrl.isBlank()) { toast("Open a page first."); return }
        val uri = try { Uri.parse(currentUrl) } catch (_: Throwable) { null }
        val scheme = uri?.scheme?.uppercase(Locale.US) ?: "UNKNOWN"
        val host = uri?.host ?: "Unknown host"
        val secure = scheme == "HTTPS"
        AlertDialog.Builder(this).setTitle("Connection information")
            .setMessage("Host: $host\nProtocol: $scheme\n\n${if (secure) "🔒 HTTPS is in use." else "⚠ This page is not using HTTPS."}\n\nUniversal Browser refuses cleartext app traffic, but individual websites can still be HTTP and should not be trusted with sensitive information.")
            .setPositiveButton("OK", null).show()
    }
'''
if 'private fun openPrivateWindow()' not in src:
    pos = src.rfind('\n}\n')
    if pos < 0:
        raise SystemExit('MainActivity class terminator not found')
    src = src[:pos] + advanced_methods + src[pos:]

required = [
    'private val privateMode',
    '.usePrivateMode(privateMode)',
    'private fun openPrivateWindow()',
    'private fun translateCurrentPage()',
    'private fun addCurrentPageToHome()',
    'private fun clearCurrentSiteData()',
    'private fun showConnectionInformation()',
    '"🕵 New private window"',
    '"🌍 Translate page"',
    '"➕ Add to home screen"',
]
missing = [x for x in required if x not in src]
if missing:
    raise SystemExit('Advanced browser upgrade incomplete; missing: ' + ', '.join(missing))

MAIN.write_text(src)
