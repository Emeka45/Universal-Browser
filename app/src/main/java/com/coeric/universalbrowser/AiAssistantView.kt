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
