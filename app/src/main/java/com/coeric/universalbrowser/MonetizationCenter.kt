package com.coeric.universalbrowser

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Centralized monetization surface for Universal Browser.
 *
 * Development builds intentionally use Google's official test IDs. Production
 * IDs stay in resources so they can be changed without changing browser code.
 */
object MonetizationCenter {
    private const val CONTAINER_TAG = "universal_monetization_container"

    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewWithTag<ViewGroup>(CONTAINER_TAG) != null) return

        val density = activity.resources.displayMetrics.density
        val widthDp = (activity.resources.displayMetrics.widthPixels / density).toInt().coerceAtLeast(320)
        val banner = AdView(activity).apply {
            adUnitId = activity.getString(R.string.admob_banner_unit_id)
            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp))
            loadAd(AdRequest.Builder().build())
        }

        val container = FrameLayout(activity).apply {
            tag = CONTAINER_TAG
            setBackgroundColor(Color.WHITE)
            elevation = 12f
            contentDescription = "Universal Browser advertisement"
        }
        container.addView(banner, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        val params = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = (6 * density).toInt()
            rightMargin = (6 * density).toInt()
            bottomMargin = (4 * density).toInt()
        }
        content.addView(container, params)
    }

    fun detach(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val container = content.findViewWithTag<ViewGroup>(CONTAINER_TAG) ?: return
        for (i in 0 until container.childCount) {
            (container.getChildAt(i) as? AdView)?.destroy()
        }
        content.removeView(container)
    }
}
