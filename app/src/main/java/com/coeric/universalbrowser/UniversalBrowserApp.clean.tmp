package com.coeric.universalbrowser

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.android.gms.ads.MobileAds
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class UniversalBrowserApp : Application() {
    @Volatile private var runtimeInstance: GeckoRuntime? = null

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) MonetizationCenter.attach(activity) }
            override fun onActivityPaused(activity: Activity) { if (activity is MainActivity) MonetizationCenter.detach(activity) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    @Synchronized
    fun getRuntime(): GeckoRuntime {
        runtimeInstance?.let { return it }
        val powerPrefs = getSharedPreferences("universal_browser_power", MODE_PRIVATE)
        val securityPrefs = getSharedPreferences("universal_security", MODE_PRIVATE)
        val textScale = powerPrefs.getFloat("text_scale", 1.0f).coerceIn(0.8f, 2.0f)
        val blockThirdParty = securityPrefs.getBoolean("block_third_party_cookies", false)
        val cookieBehavior = if (blockThirdParty) ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS else ContentBlocking.CookieBehavior.ACCEPT_ALL
        val settings = GeckoRuntimeSettings.Builder()
            .forceUserScalableEnabled(true)
            .doubleTapZoomingEnabled(true)
            .automaticFontSizeAdjustment(false)
            .fontSizeFactor(textScale)
            .fontInflation(true)
            .globalPrivacyControlEnabled(true)
            .contentBlocking(ContentBlocking.Settings.Builder()
                .antiTracking(ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP)
                .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                .cookieBehavior(cookieBehavior)
                .cookieBehaviorPrivateMode(cookieBehavior)
                .build())
            .build()
        return GeckoRuntime.create(this, settings).also { runtimeInstance = it; it.warmUp() }
    }
}
