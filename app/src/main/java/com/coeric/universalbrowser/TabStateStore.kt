package com.coeric.universalbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight crash/restart recovery for browser tabs. */
class TabStateStore(context: Context) {
    data class Tab(val id: String, val url: String, val title: String, val privateMode: Boolean, val active: Boolean)
    private val prefs = context.getSharedPreferences("universal_tab_state", Context.MODE_PRIVATE)

    fun save(tabs: List<Tab>) {
        val json = JSONArray()
        tabs.take(50).forEach { tab ->
            json.put(JSONObject().apply {
                put("id", tab.id); put("url", tab.url); put("title", tab.title)
                put("private", tab.privateMode); put("active", tab.active)
            })
        }
        prefs.edit().putString("tabs", json.toString()).apply()
    }

    fun restore(): List<Tab> {
        val raw = prefs.getString("tabs", null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val o = json.getJSONObject(i)
                    val url = o.optString("url").trim()
                    if (url.isNotBlank()) add(Tab(o.optString("id"), url, o.optString("title"), o.optBoolean("private"), o.optBoolean("active")))
                }
            }
        } catch (_: Throwable) { emptyList() }
    }

    fun clear() = prefs.edit().remove("tabs").apply()
}
