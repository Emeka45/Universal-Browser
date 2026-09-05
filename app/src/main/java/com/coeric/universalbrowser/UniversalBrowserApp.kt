package com.coeric.universalbrowser

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime

class UniversalBrowserApp : Application() {
    @Volatile
    private var runtime: GeckoRuntime? = null

    @Synchronized
    fun obtainRuntime(): GeckoRuntime {
        runtime?.let { return it }
        return GeckoRuntime.create(this).also { runtime = it }
    }
}
