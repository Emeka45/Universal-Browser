package com.coeric.universalbrowser

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
