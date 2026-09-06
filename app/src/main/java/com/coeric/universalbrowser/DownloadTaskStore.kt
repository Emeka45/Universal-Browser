package com.coeric.universalbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent metadata for browser downloads across activity/process restarts. */
class DownloadTaskStore(context: Context) {
    data class Task(
        val id: String,
        val url: String,
        val title: String,
        val state: String,
        val bytes: Long,
        val total: Long,
        val createdAt: Long,
        val referer: String = "",
        val localUri: String? = null,
        val mimeType: String = "application/octet-stream",
        val tempPath: String? = null
    )

    private val prefs = context.getSharedPreferences("universal_download_tasks", Context.MODE_PRIVATE)

    @Synchronized fun upsert(task: Task) {
        val tasks = all().filterNot { it.id == task.id }.toMutableList()
        tasks.add(0, task)
        save(tasks)
    }

    @Synchronized fun update(
        id: String,
        url: String? = null,
        title: String? = null,
        state: String? = null,
        bytes: Long? = null,
        total: Long? = null,
        referer: String? = null,
        localUri: String? = null,
        mimeType: String? = null,
        tempPath: String? = null,
        clearTempPath: Boolean = false
    ) {
        val old = all().firstOrNull { it.id == id } ?: return
        upsert(old.copy(
            url = url ?: old.url,
            title = title ?: old.title,
            state = state ?: old.state,
            bytes = bytes ?: old.bytes,
            total = total ?: old.total,
            referer = referer ?: old.referer,
            localUri = localUri ?: old.localUri,
            mimeType = mimeType ?: old.mimeType,
            tempPath = if (clearTempPath) null else tempPath ?: old.tempPath
        ))
    }

    fun updateState(id: String, state: String) = update(id, state = state)
    fun updateBytes(id: String, bytes: Long, total: Long) = update(id, bytes = bytes, total = total)

    @Synchronized fun all(): List<Task> {
        val raw = prefs.getString("tasks", null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val o = json.getJSONObject(i)
                    add(Task(
                        o.optString("id"),
                        o.optString("url"),
                        o.optString("title"),
                        o.optString("state"),
                        o.optLong("bytes"),
                        o.optLong("total"),
                        o.optLong("createdAt"),
                        o.optString("referer"),
                        o.optString("localUri").takeIf { it.isNotBlank() },
                        o.optString("mimeType", "application/octet-stream"),
                        o.optString("tempPath").takeIf { it.isNotBlank() }
                    ))
                }
            }
        } catch (_: Throwable) { emptyList() }
    }

    fun clearFinished() = save(all().filter { it.state !in setOf("completed", "failed", "cancelled") })

    private fun save(tasks: List<Task>) {
        prefs.edit().putString("tasks", JSONArray().apply {
            tasks.take(200).forEach { t -> put(JSONObject().apply {
                put("id", t.id); put("url", t.url); put("title", t.title); put("state", t.state)
                put("bytes", t.bytes); put("total", t.total); put("createdAt", t.createdAt)
                put("referer", t.referer); t.localUri?.let { put("localUri", it) }; put("mimeType", t.mimeType)
                t.tempPath?.let { put("tempPath", it) }
            }) }
        }.toString()).apply()
    }
}
