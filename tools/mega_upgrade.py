from pathlib import Path

ROOT = Path('.')
MAIN = ROOT / 'app/src/main/java/com/coeric/universalbrowser/MainActivity.kt'

src = MAIN.read_text()

def once(needle: str, replacement: str):
    global src
    if replacement.strip() in src:
        return
    if needle not in src:
        raise SystemExit(f'Missing anchor: {needle}')
    src = src.replace(needle, needle + replacement, 1)

once('import android.content.Intent\n', 'import android.content.Context\nimport android.view.inputmethod.InputMethodManager\n')
once('    private var currentUrl = ""\n', '    private val browserData by lazy { BrowserDataStore(this) }\n    private var desktopMode = false\n')
once('                currentUrl = url ?: currentUrl\n', '                if (!url.isNullOrBlank()) browserData.recordVisit(url, url)\n')

home_anchor = '        content.addView(featureCard("Media downloads", "Play supported media, then download it in one tap", "↓") { showMediaDownloadInfo() }, featureParams())\n'
home_insert = '''        content.addView(featureCard("Power tools", "Bookmarks, history, desktop site, find, sharing and privacy", "⚙") { showPowerTools() }, featureParams())\n        content.addView(featureCard("Native AI assistant", "Ask about the current page, summarize, explain, rewrite or translate", "AI") { showAiAssistant() }, featureParams())\n'''
once(home_anchor, home_insert)

src = src.replace('A lightweight Gecko browser for Android Go devices', 'A full-featured Gecko browser for Android')
src = src.replace('A lightweight Android browser', 'A full-featured Gecko browser')

methods = r'''

    private fun showPowerTools() {
        val items = arrayOf(
            "⭐ Add current page to bookmarks", "🔖 Bookmarks", "🕘 History",
            if (desktopMode) "📱 Switch to mobile site" else "🖥 Desktop site",
            "🔎 Find in page", "↗ Share current page", "⬇ Downloads",
            "🧹 Clear browsing data", "⚙ Browser settings"
        )
        AlertDialog.Builder(this).setTitle("Universal Browser").setItems(items) { _, which ->
            when (which) {
                0 -> addCurrentBookmark(); 1 -> showBookmarks(); 2 -> showHistory()
                3 -> toggleDesktopSite(); 4 -> findInPage(); 5 -> shareCurrentPage()
                6 -> openDownloads(); 7 -> clearBrowsingData(); 8 -> showBrowserSettings()
            }
        }.show()
    }

    private fun addCurrentBookmark() {
        if (currentUrl.isBlank()) { toast("Open a page first."); return }
        browserData.addBookmark(currentUrl, currentUrl); toast("Bookmarked")
    }

    private fun showBookmarks() {
        val entries = browserData.bookmarks()
        if (entries.isEmpty()) { toast("No bookmarks yet."); return }
        val labels = entries.map { "${it.title}\n${it.url}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Bookmarks").setItems(labels) { _, which -> navigate(entries[which].url) }
            .setNegativeButton("Close", null).show()
    }

    private fun showHistory() {
        val entries = browserData.history()
        if (entries.isEmpty()) { toast("No history yet."); return }
        val labels = entries.take(100).map { "${it.title}\n${it.url}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("History").setItems(labels) { _, which -> navigate(entries[which].url) }
            .setNeutralButton("Clear") { _, _ -> browserData.clearHistory(); toast("History cleared") }
            .setNegativeButton("Close", null).show()
    }

    private fun toggleDesktopSite() {
        if (!::session.isInitialized) { toast("Open a page first."); return }
        desktopMode = !desktopMode
        session.settings.setUserAgentMode(if (desktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        session.settings.setViewportMode(if (desktopMode) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        session.reload()
        toast(if (desktopMode) "Desktop site enabled" else "Mobile site enabled")
    }

    private fun findInPage() {
        if (!::session.isInitialized) { toast("Open a page first."); return }
        val input = EditText(this).apply { hint = "Find on this page"; isSingleLine = true }
        AlertDialog.Builder(this).setTitle("Find in page").setView(input)
            .setNegativeButton("Close") { _, _ -> session.finder.clear() }
            .setPositiveButton("Find") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    session.finder.setDisplayFlags(org.mozilla.geckoview.GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL)
                    session.finder.find(q, org.mozilla.geckoview.GeckoSession.FINDER_FIND_FORWARD)
                }
            }.show()
        input.requestFocus()
        input.postDelayed({ (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }, 150)
    }

    private fun shareCurrentPage() {
        if (currentUrl.isBlank()) { toast("Open a page first."); return }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, currentUrl); putExtra(Intent.EXTRA_TITLE, "Share from Universal Browser")
        }, "Share page"))
    }

    private fun openDownloads() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                type = "resource/folder"
                data = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toURI().toString())
            })
        } catch (_: Throwable) { toast("Open the Downloads app to view downloaded files.") }
    }

    private fun clearBrowsingData() {
        AlertDialog.Builder(this).setTitle("Clear browsing data")
            .setMessage("Clear history and site cookies? Downloaded files and bookmarks are kept.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                browserData.clearHistory(); CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush(); toast("Browsing data cleared")
            }.show()
    }

    private fun showBrowserSettings() {
        AlertDialog.Builder(this).setTitle("Browser settings")
            .setItems(arrayOf("Extension stores", "Privacy information", "About Universal Browser")) { _, which ->
                when (which) {
                    0 -> showWebStores()
                    1 -> AlertDialog.Builder(this).setTitle("Privacy").setMessage("Universal Browser uses GeckoView and can clear site cookies and browsing history. Extension permissions remain under the Extensions manager.").setPositiveButton("OK", null).show()
                    2 -> showAbout()
                }
            }.show()
    }

    private fun showAiAssistant() {
        if (!::session.isInitialized || currentUrl.isBlank()) {
            AlertDialog.Builder(this).setTitle("Universal AI").setMessage("Open a webpage first. The native AI assistant works with the current page and your prompt through a secure HTTPS provider endpoint.").setPositiveButton("OK", null).show()
            return
        }
        AiAssistantView.show(this, currentUrl)
    }
'''

if 'private fun showPowerTools()' not in src:
    pos = src.rfind('\n}\n')
    if pos < 0:
        raise SystemExit('Final class brace not found')
    src = src[:pos] + methods + src[pos:]

MAIN.write_text(src)

(ROOT / 'app/src/main/java/com/coeric/universalbrowser/BrowserDataStore.kt').write_text(r'''package com.coeric.universalbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BrowserDataStore(context: Context) {
    data class Entry(val title: String, val url: String, val time: Long)
    private val prefs = context.getSharedPreferences("universal_browser_data", Context.MODE_PRIVATE)

    fun addBookmark(url: String, title: String) {
        val list = bookmarks().filterNot { it.url == url }.toMutableList()
        list.add(0, Entry(title.ifBlank { url }, url, System.currentTimeMillis()))
        save("bookmarks", list.take(200))
    }
    fun bookmarks(): List<Entry> = load("bookmarks")

    fun recordVisit(url: String, title: String) {
        val list = history().filterNot { it.url == url }.toMutableList()
        list.add(0, Entry(title.ifBlank { url }, url, System.currentTimeMillis()))
        save("history", list.take(500))
    }
    fun history(): List<Entry> = load("history")
    fun clearHistory() = prefs.edit().remove("history").apply()

    private fun save(key: String, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().apply { put("title", it.title); put("url", it.url); put("time", it.time) }) }
        prefs.edit().putString(key, array.toString()).apply()
    }
    private fun load(key: String): List<Entry> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(Entry(o.optString("title"), o.optString("url"), o.optLong("time")))
                }
            }
        } catch (_: Throwable) { emptyList() }
    }
}
''')

(ROOT / 'app/src/main/java/com/coeric/universalbrowser/AiAssistantView.kt').write_text(r'''package com.coeric.universalbrowser

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

object AiAssistantView {
    private const val PREFS = "universal_ai"
    private const val ENDPOINT = "endpoint"

    fun show(context: Context, pageUrl: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 4, 22, 4) }
        val endpoint = EditText(context).apply { hint = "Secure AI endpoint (HTTPS)"; setText(prefs.getString(ENDPOINT, "") ?: ""); isSingleLine = true }
        val prompt = EditText(context).apply { hint = "Ask about this page..."; minLines = 3; gravity = Gravity.TOP }
        root.addView(endpoint); root.addView(prompt)
        AlertDialog.Builder(context).setTitle("Universal AI")
            .setMessage("Native page assistant • provider agnostic")
            .setView(root).setNegativeButton("Close", null)
            .setNeutralButton("Save endpoint") { _, _ -> prefs.edit().putString(ENDPOINT, endpoint.text.toString().trim()).apply(); Toast.makeText(context, "AI endpoint saved", Toast.LENGTH_SHORT).show() }
            .setPositiveButton("Ask") { _, _ ->
                prefs.edit().putString(ENDPOINT, endpoint.text.toString().trim()).apply()
                val q = prompt.text.toString().trim().ifBlank { "Summarize this page in clear bullet points." }
                call(context, endpoint.text.toString().trim(), pageUrl, q)
            }.show()
    }

    private fun call(context: Context, endpoint: String, pageUrl: String, prompt: String) {
        if (!endpoint.startsWith("https://")) { Toast.makeText(context, "Set an HTTPS AI endpoint first", Toast.LENGTH_LONG).show(); return }
        Toast.makeText(context, "Universal AI is processing…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 15000; conn.readTimeout = 30000
                conn.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().apply { put("url", pageUrl); put("prompt", prompt); put("source", "Universal Browser") }.toString().toByteArray(StandardCharsets.UTF_8)
                conn.outputStream.use { it.write(body) }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                (context as? android.app.Activity)?.runOnUiThread {
                    AlertDialog.Builder(context).setTitle("Universal AI result").setMessage(text.take(12000)).setPositiveButton("OK", null).show()
                }
            } catch (e: Throwable) {
                (context as? android.app.Activity)?.runOnUiThread { Toast.makeText(context, "AI request failed: ${e.message ?: "network error"}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
''')
