package com.coeric.universalbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent metadata for downloads so the UI can survive activity restarts. */
class DownloadTaskStore(context: Context) {
    data class Task(val id: String, val url: String, val title: String, val state: String, val bytes: Long, val total: Long, val createdAt: Long)
    private val prefs = context.getSharedPreferences("universal_download_tasks", Context.MODE_PRIVATE)

    fun upsert(task: Task) {
        val tasks = all().filterNot { it.id == task.id }.toMutableList()
        tasks.add(0, task)
        val json = JSONArray()
        tasks.take(200).forEach { t -> json.put(JSONObject().apply {
            put("id", t.id); put("url", t.url); put("title", t.title); put("state", t.state)
            put("bytes", t.bytes); put("total", t.total); put("createdAt", t.createdAt)
        }) }
        prefs.edit().putString("tasks", json.toString()).apply()
    }

    fun all(): List<Task> {
        val raw = prefs.getString("tasks", null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val o = json.getJSONObject(i)
                    add(Task(o.optString("id"), o.optString("url"), o.optString("title"), o.optString("state"), o.optLong("bytes"), o.optLong("total"), o.optLong("createdAt")))
                }
            }
        } catch (_: Throwable) { emptyList() }
    }

    fun clearFinished() {
        val remaining = all().filter { it.state !in setOf("completed", "failed") }
        prefs.edit().putString("tasks", JSONArray().apply { remaining.forEach { put(JSONObject().apply { put("id", it.id); put("url", it.url); put("title", it.title); put("state", it.state); put("bytes", it.bytes); put("total", it.total); put("createdAt", it.createdAt) }) } }.toString()).apply()
    }
}
