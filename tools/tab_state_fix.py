from pathlib import Path

MAIN = Path('app/src/main/java/com/coeric/universalbrowser/MainActivity.kt')
src = MAIN.read_text()

old_location = '                tabManager.updateLabel(session, url ?: "New tab")\n'
new_location = '                tabManager.updateUrl(session, url ?: "")\n                tabManager.updateLabel(session, url ?: "New tab")\n'
if new_location not in src:
    if src.count(old_location) != 1:
        raise SystemExit('Tab state location anchor not found exactly once')
    src = src.replace(old_location, new_location, 1)

old_switch = '        val url = tab.session.toString().let { currentUrl }.takeIf { currentUrl.isNotBlank() }\n'
new_switch = '        currentUrl = tab.url\n        val url = tab.url.takeIf { it.isNotBlank() }\n'
if new_switch not in src:
    if src.count(old_switch) != 1:
        raise SystemExit('Tab switch URL anchor not found exactly once')
    src = src.replace(old_switch, new_switch, 1)

required = [
    'tabManager.updateUrl(session, url ?: "")',
    'currentUrl = tab.url',
    'val url = tab.url.takeIf { it.isNotBlank() }',
]
missing = [x for x in required if x not in src]
if missing:
    raise SystemExit('Tab state fix incomplete: ' + ', '.join(missing))

MAIN.write_text(src)
