package com.coeric.universalbrowser

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Store bridge: presents extension stores inside Universal Browser and applies
 * a store-specific browser identity before navigation. This prevents the UX
 * failure where a store simply tells the user to download another browser.
 */
object ExtensionStoreCenter {
    private const val CHROME_DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    private const val OPERA_DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 OPR/120.0.0.0"
    private const val EDGE_DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0"

    data class Store(val name: String, val url: String, val userAgent: String?)

    private val stores = listOf(
        Store("Chrome Web Store", "https://chromewebstore.google.com/", CHROME_DESKTOP_UA),
        Store("Opera Add-ons", "https://addons.opera.com/en/extensions/", OPERA_DESKTOP_UA),
        Store("Microsoft Edge Add-ons", "https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home", EDGE_DESKTOP_UA),
        Store("Firefox Add-ons", "https://addons.mozilla.org/android/", null)
    )

    fun show(context: Context, sessionProvider: () -> GeckoSession?, navigate: (String) -> Unit) {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(context), 4.dp(context), 14.dp(context), 10.dp(context))
        }
        stores.forEach { store ->
            val row = TextView(context).apply {
                text = "🧩  ${store.name}\n     Browse extensions and install them through Universal Browser"
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
                (context as? android.app.Activity)?.startActivityForResult(
                    android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "application/*"
                    },
                    ExtensionManager.PICK_EXTENSION_REQUEST
                )
            }
        })
        list.addView(TextView(context).apply {
            text = "View installed extensions"
            textSize = 14f
            setTextColor(Color.rgb(101, 72, 255))
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
            setOnClickListener { ExtensionManager.showInstalled(context) }
        })
        AlertDialog.Builder(context)
            .setTitle("Extension Stores")
            .setView(list)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()
}
