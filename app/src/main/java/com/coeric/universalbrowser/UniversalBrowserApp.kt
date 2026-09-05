package com.coeric.universalbrowser

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime

class UniversalBrowserApp : Application() {
    @Volatile
    private var runtimeInstance: GeckoRuntime? = null

    @Synchronized
    fun getRuntime(): GeckoRuntime {
        runtimeInstance?.let { return it }
        return GeckoRuntime.create(this).also { runtimeInstance = it }
    }
}
