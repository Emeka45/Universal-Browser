package com.coeric.universalbrowser

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.android.gms.ads.MobileAds
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class UniversalBrowserApp : Application() {
    @Volatile
    private var runtimeInstance: GeckoRuntime? = null

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) MonetizationCenter.attach(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (activity is MainActivity) MonetizationCenter.detach(activity)
            }
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

        val settings = GeckoRuntimeSettings.Builder()
            // Required for addons.mozilla.org to communicate with the browser's
            // WebExtension controller and offer in-page extension installation.
            .extensionsWebAPIEnabled(true)
            // Preserve browser-quality pinch and double-tap zoom even on pages
            // that attempt to disable user scaling.
            .forceUserScalableEnabled(true)
            .doubleTapZoomingEnabled(true)
            .automaticFontSizeAdjustment(false)
            .fontSizeFactor(1.0f)
            .fontInflation(true)
            // Privacy defaults suitable for a general-purpose browser.
            .globalPrivacyControl(true)
            .fingerprintingProtection(true)
            .build()

        return GeckoRuntime.create(this, settings).also { runtimeInstance = it }
    }
}
