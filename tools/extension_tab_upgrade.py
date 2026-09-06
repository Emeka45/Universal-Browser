from pathlib import Path

MAIN = Path('app/src/main/java/com/coeric/universalbrowser/MainActivity.kt')
src = MAIN.read_text()


def once(needle: str, replacement: str, label: str):
    global src
    if replacement in src:
        return
    count = src.count(needle)
    if count != 1:
        raise SystemExit(f'Extension tab upgrade anchor for {label} expected exactly once, found {count}')
    src = src.replace(needle, replacement, 1)


once(
    '    private lateinit var tabManager: BrowserTabManager\n',
    '    private lateinit var tabManager: BrowserTabManager\n    private lateinit var extensionTabBridge: ExtensionTabBridge\n',
    'extension bridge field'
)

once(
    '        tabManager = BrowserTabManager(getRuntime())\n',
    '        tabManager = BrowserTabManager(getRuntime())\n        extensionTabBridge = ExtensionTabBridge(\n            getRuntime().webExtensionController,\n            tabManager,\n            { created -> bindSession(created) },\n            { closed -> if (closed === session && tabManager.active() != null) bindSession(tabManager.active()!!.session) },\n            { active -> if (active !== session) bindSession(active) }\n        )\n        extensionTabBridge.attachInstalledExtensions()\n',
    'extension bridge initialization'
)

once(
    '        session = target\n',
    '        session = target\n        extensionTabBridge.bindSession(target)\n',
    'extension session binding'
)

once(
    '        tabManager.activate(tabManager.indexOf(session))\n        installMediaDetector()\n',
    '        tabManager.activate(tabManager.indexOf(session))\n        extensionTabBridge.notifyActive(session)\n        extensionTabBridge.attachInstalledExtensions()\n        installMediaDetector()\n',
    'extension active-tab notification'
)

# Re-scan after returning to the foreground so newly installed extensions are
# attached without requiring an app restart.
if 'override fun onResume()' not in src:
    marker = '    @Suppress("DEPRECATION")\n    override fun onBackPressed()'
    if marker not in src:
        raise SystemExit('Could not locate lifecycle insertion point')
    lifecycle = '''    override fun onResume() {
        super.onResume()
        if (::extensionTabBridge.isInitialized) extensionTabBridge.attachInstalledExtensions()
    }

'''
    src = src.replace(marker, lifecycle + marker, 1)

required = [
    'private lateinit var extensionTabBridge: ExtensionTabBridge',
    'extensionTabBridge = ExtensionTabBridge(',
    'extensionTabBridge.attachInstalledExtensions()',
    'extensionTabBridge.bindSession(target)',
    'extensionTabBridge.notifyActive(session)',
    'override fun onResume()',
]
missing = [x for x in required if x not in src]
if missing:
    raise SystemExit('Extension tab upgrade incomplete; missing: ' + ', '.join(missing))

MAIN.write_text(src)
