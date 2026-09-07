package com.coeric.universalbrowser

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/** Presents major extension stores inside Universal Browser with store-specific identity. */
object ExtensionStoreCenter {
    private const val CHROME_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    private const val OPERA_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 OPR/120.0.0.0"
    private const val EDGE_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0"

    data class Store(val name: String, val url: String, val userAgent: String?)

    private val stores = listOf(
        Store("Chrome Web Store", "https://chromewebstore.google.com/", CHROME_UA),
        Store("Opera Add-ons", "https://addons.opera.com/en/extensions/", OPERA_UA),
        Store("Microsoft Edge Add-ons", "https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home", EDGE_UA),
        Store("Firefox Add-ons", "https://addons.mozilla.org/android/", null)
    )

    fun show(context: Context, sessionProvider: () -> GeckoSession?, navigate: (String) -> Unit) {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(context), 4.dp(context), 14.dp(context), 10.dp(context))
        }
        stores.forEach { store ->
            val row = TextView(context).apply {
                text = "🧩  ${store.name}\n     Browse extensions and install supported add-ons"
                textSize = 14f
                setTextColor(Color.rgb(27, 26, 39))
                setPadding(16.dp(context), 14.dp(context), 16.dp(context), 14.dp(context))
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    sessionProvider()?.let { session ->
                        store.userAgent?.let { session.getSettings().setUserAgentOverride(it) }
                        session.getSettings().setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
                    }
                    navigate(store.url)
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp(context) })
        }
        list.addView(TextView(context).apply {
            text = "Install an extension file from your phone"
            textSize = 14f
            setTextColor(Color.rgb(101, 72, 255))
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
            setOnClickListener {
                (context as? android.app.Activity)?.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-xpinstall", "application/x-chrome-extension", "application/zip", "application/octet-stream"))
                }, ExtensionManager.PICK_EXTENSION_REQUEST)
            }
        })
        list.addView(TextView(context).apply {
            text = "View installed extensions"
            textSize = 14f
            setTextColor(Color.rgb(101, 72, 255))
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
            setOnClickListener { ExtensionManager.showInstalled(context) }
        })
        AlertDialog.Builder(context).setTitle("Extension Stores").setView(list).setNegativeButton("Close", null).show()
    }

    private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()
}
