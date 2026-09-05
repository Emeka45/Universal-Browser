package com.coeric.universalbrowser

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class UniversalBrowserApp : Application() {
    @Volatile
    private var runtime: GeckoRuntime? = null

    @Synchronized
    fun getRuntime(): GeckoRuntime {
        runtime?.let { return it }

        // Keep the browser usable on low-memory Android Go devices. GeckoView
        // normally runs multiple content processes; disabling Fission here
        // reduces the process/RAM footprint for this single-tab browser.
        val settings = GeckoRuntimeSettings.Builder()
            .fissionEnabled(false)
            .glMsaaLevel(0)
            .consoleOutput(false)
            .debugLogging(false)
            .build()

        return GeckoRuntime.create(this, settings).also { runtime = it }
    }
}
