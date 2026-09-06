package com.coeric.universalbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent crash/restart state for normal tabs. Private tabs are never persisted. */
class TabStateStore(context: Context) {
    data class Tab(
        val id: String,
        val url: String,
        val title: String,
        val privateMode: Boolean,
        val active: Boolean,
        val sessionState: String? = null
    )

    private val prefs = context.getSharedPreferences("universal_tab_state", Context.MODE_PRIVATE)

    fun save(tabs: List<Tab>) {
        val json = JSONArray()
        tabs.filterNot { it.privateMode }.take(50).forEach { tab ->
            json.put(JSONObject().apply {
                put("id", tab.id)
                put("url", tab.url)
                put("title", tab.title)
                put("private", false)
                put("active", tab.active)
                tab.sessionState?.takeIf { it.isNotBlank() }?.let { put("state", it) }
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
                    if (url.isNotBlank()) {
                        add(Tab(
                            id = o.optString("id"),
                            url = url,
                            title = o.optString("title"),
                            privateMode = false,
                            active = o.optBoolean("active"),
                            sessionState = o.optString("state").takeIf { it.isNotBlank() }
                        ))
                    }
                }
            }
        } catch (_: Throwable) { emptyList() }
    }

    fun clear() = prefs.edit().remove("tabs").apply()
}
