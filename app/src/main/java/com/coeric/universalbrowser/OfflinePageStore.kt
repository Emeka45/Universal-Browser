package com.coeric.universalbrowser

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stores reader-style offline page snapshots as standalone UTF-8 HTML files. */
object OfflinePageStore {
    fun save(context: Context, title: String, url: String, extractedText: String): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "offline-pages")
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safe = title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(70).ifBlank { "offline-page" }
        val file = File(dir, "$stamp-$safe.html")
        val html = """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>${escape(title)}</title><meta name="source-url" content="${escape(url)}">
            <style>body{font-family:sans-serif;max-width:760px;margin:0 auto;padding:24px;line-height:1.65}h1{line-height:1.2}small{color:#777}pre{white-space:pre-wrap;font:inherit}</style></head>
            <body><h1>${escape(title.ifBlank { "Offline page" })}</h1><small>${escape(url)}</small><hr><pre>${escape(extractedText)}</pre></body></html>
        """.trimIndent()
        file.writeText(html, Charsets.UTF_8)
        return file
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
