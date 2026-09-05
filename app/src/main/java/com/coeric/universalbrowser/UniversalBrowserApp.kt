package com.coeric.universalbrowser

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class UniversalBrowserApp : Application() {
    @Volatile
    private var runtimeInstance: GeckoRuntime? = null

    @Synchronized
    fun getRuntime(): GeckoRuntime {
        runtimeInstance?.let { return it }

        val settings = GeckoRuntimeSettings.Builder()
            // Required for addons.mozilla.org to communicate with the browser's
            // WebExtension controller and offer in-page extension installation.
            .extensionsWebAPIEnabled(true)
            .build()

        return GeckoRuntime.create(this, settings).also { runtimeInstance = it }
    }
}
