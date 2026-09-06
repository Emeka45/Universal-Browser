from pathlib import Path

MAIN = Path('app/src/main/java/com/coeric/universalbrowser/MainActivity.kt')
p = MAIN.read_text()

# Add the power-tools entry point to the toolbar exactly once.
anchor = 'row.addView(toolbarButton("⋮", 23f) { showBrowserMenu() })'
replacement = '''row.addView(toolbarButton("⚡", 21f) { BrowserPowerCenter.show(this, { if (::session.isInitialized) session else null }, { currentUrl }, { if (::session.isInitialized) session.reload() }) })\n        ''' + anchor
if 'BrowserPowerCenter.show(this' not in p:
    if anchor not in p:
        raise SystemExit('toolbar anchor not found')
    p = p.replace(anchor, replacement, 1)

# Apply persistent desktop/zoom preferences whenever a session is created.
old = 'session = GeckoSession()\n        session.contentDelegate'
new = 'session = GeckoSession()\n        BrowserPowerCenter.applyPreferences(this, session)\n        session.contentDelegate'
if 'BrowserPowerCenter.applyPreferences(this, session)' not in p:
    if old not in p:
        raise SystemExit('session initialization anchor not found')
    p = p.replace(old, new, 1)

MAIN.write_text(p)

POWER = Path('app/src/main/java/com/coeric/universalbrowser/BrowserPowerCenter.kt')
q = POWER.read_text()
marker = '    private fun toggleDesktop(activity: Activity, session: GeckoSession, reload: () -> Unit) {'
method = '''    fun applyPreferences(activity: Activity, session: GeckoSession) {\n        val prefs = activity.getSharedPreferences(PREFS, 0)\n        val desktop = prefs.getBoolean(DESKTOP, false)\n        session.settings.setUserAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)\n        session.settings.setViewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)\n    }\n\n'''
if 'fun applyPreferences(' not in q:
    if marker not in q:
        raise SystemExit('power center anchor not found')
    q = q.replace(marker, method + marker, 1)
    POWER.write_text(q)
