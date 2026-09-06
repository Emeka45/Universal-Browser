package com.coeric.universalbrowser

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
        val endpoint = EditText(context).apply {
            hint = "Universal AI Gateway (HTTPS /v1/chat)"
            setText(prefs.getString(ENDPOINT, "") ?: "")
            isSingleLine = true
        }
        val prompt = EditText(context).apply { hint = "Ask about this page..."; minLines = 3; gravity = Gravity.TOP }
        root.addView(endpoint); root.addView(prompt)
        AlertDialog.Builder(context).setTitle("Universal AI")
            .setMessage("Secure native assistant • provider agnostic • server-side API key")
            .setView(root).setNegativeButton("Close", null)
            .setNeutralButton("Save gateway") { _, _ ->
                prefs.edit().putString(ENDPOINT, endpoint.text.toString().trim()).apply()
                Toast.makeText(context, "Universal AI gateway saved", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Ask") { _, _ ->
                prefs.edit().putString(ENDPOINT, endpoint.text.toString().trim()).apply()
                val q = prompt.text.toString().trim().ifBlank { "Summarize this page in clear bullet points." }
                call(context, endpoint.text.toString().trim(), pageUrl, q)
            }.show()
    }

    private fun call(context: Context, endpoint: String, pageUrl: String, prompt: String) {
        if (!endpoint.startsWith("https://")) {
            Toast.makeText(context, "Set your deployed Universal AI Gateway HTTPS endpoint first", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(context, "Universal AI is processing…", Toast.LENGTH_SHORT).show()
        Thread {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Universal-Client", "android-browser")
                val body = JSONObject().apply {
                    put("url", pageUrl)
                    put("prompt", prompt)
                    put("source", "Universal Browser")
                }.toString().toByteArray(StandardCharsets.UTF_8)
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val payload = runCatching { JSONObject(raw) }.getOrNull()
                if (code !in 200..299) throw IllegalStateException(payload?.optString("error").orEmpty().ifBlank { "Gateway HTTP $code" })
                val answer = payload?.optString("answer").orEmpty().ifBlank { raw }
                (context as? android.app.Activity)?.runOnUiThread {
                    AlertDialog.Builder(context).setTitle("Universal AI result").setMessage(answer.take(12000)).setPositiveButton("OK", null).show()
                }
            } catch (e: Throwable) {
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "AI request failed: ${e.message ?: "network error"}", Toast.LENGTH_LONG).show()
                }
            } finally { conn?.disconnect() }
        }.start()
    }
}
